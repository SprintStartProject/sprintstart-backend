package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.FacetCountResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Selection
import java.util.UUID

/**
 * The facet counts beside the artifact list: for each facet, how many artifacts each value would match.
 *
 * A facet is counted over the artifacts that pass every active filter but its own.
 */
internal class ArtifactFacetCounter(
    private val entityManager: EntityManager,
) {
    fun facets(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): ArtifactFacetsResponse {
        return ArtifactFacetsResponse(
            types = computeTypeFacets(projectId, criteria),
            sources = computeSourceFacets(projectId, criteria),
            formats = computeFormatFacets(projectId, criteria),
            repositories = computeRepositoryFacets(projectId, criteria),
            languages = computeLanguageFacets(projectId, criteria),
        )
    }

    /** What one facet asks of the artifacts: the columns it reads, its own conditions, and its grouping. */
    private class FacetQuery(
        val columns: List<Selection<*>>,
        val conditions: List<Predicate> = emptyList(),
        val groupBy: Expression<*>? = null,
    )

    /**
     * The rows one facet counts from: the artifacts of [projectId] that pass every active filter but
     * [kind]'s own — a facet that narrowed itself could never offer the other values — and the
     * facet's own conditions.
     */
    private fun facetRows(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        kind: FacetKind,
        facet: (CriteriaBuilder, Root<Artifact>) -> FacetQuery,
    ): List<Array<Any>> {
        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(Array<Any>::class.java)
        val root = query.from(Artifact::class.java)
        val join = root.join<Artifact, UUID>("projectIdsInternal")
        val spec = facet(cb, root)
        query.select(cb.array(*spec.columns.toTypedArray()))
        val predicates = ArtifactFilterPredicates.predicates(cb, root, join, projectId, criteria, kind)
        query.where(*(predicates + spec.conditions).toTypedArray())
        spec.groupBy?.let { query.groupBy(it) }
        return entityManager.createQuery(query).resultList
    }

    private fun computeTypeFacets(projectId: UUID, criteria: ArtifactFilterCriteria): List<FacetCountResponse> {
        val typeCountsMap = mutableMapOf<ArtifactType, Long>()
        val rows = facetRows(projectId, criteria, FacetKind.TYPES) { cb, root ->
            val type = root.get<ArtifactType>("artifactType")
            FacetQuery(listOf(type, cb.countDistinct(root.get<UUID>("id"))), groupBy = type)
        }
        for (row in rows) {
            val type = row[0] as ArtifactType
            val count = (row[1] as Number).toLong()
            typeCountsMap[type] = count
        }
        criteria.types?.forEach { type ->
            typeCountsMap.putIfAbsent(type, 0L)
        }
        return typeCountsMap.map { (type, count) ->
            FacetCountResponse(type.name, count)
        }
    }

    private fun computeSourceFacets(projectId: UUID, criteria: ArtifactFilterCriteria): List<FacetCountResponse> {
        val sourceCountsMap = mutableMapOf<SourceSystem, Long>()
        val rows = facetRows(projectId, criteria, FacetKind.SOURCES) { cb, root ->
            val source = root.get<SourceSystem>("sourceSystem")
            FacetQuery(listOf(source, cb.countDistinct(root.get<UUID>("id"))), groupBy = source)
        }
        for (row in rows) {
            val source = row[0] as SourceSystem
            val count = (row[1] as Number).toLong()
            sourceCountsMap[source] = count
        }
        criteria.sources?.forEach { source ->
            sourceCountsMap.putIfAbsent(source, 0L)
        }
        return sourceCountsMap.map { (source, count) ->
            FacetCountResponse(source.name, count)
        }
    }

    private fun computeFormatFacets(projectId: UUID, criteria: ArtifactFilterCriteria): List<FacetCountResponse> {
        val formatCountsMap = mutableMapOf(
            UploadFormat.PDF to 0L,
            UploadFormat.MARKDOWN to 0L,
            UploadFormat.IMAGE to 0L,
            UploadFormat.OTHER to 0L,
        )
        val rows = facetRows(projectId, criteria, FacetKind.FORMATS) { cb, root ->
            FacetQuery(
                columns = listOf("title", "sourceUrl", "sourceId", "mime", "language").map { root.get<String?>(it) },
                conditions = listOf(cb.equal(root.get<SourceSystem>("sourceSystem"), SourceSystem.UPLOAD)),
            )
        }
        for (row in rows) {
            val title = row[0] as? String
            val sourceUrl = row[1] as? String
            val sourceId = row[2] as String
            val mime = row[3] as? String
            val language = row[4] as? String
            val format = ArtifactFacetRepositoryImpl.classifyUploadFormat(title, sourceUrl, sourceId, mime, language)
            formatCountsMap[format] = (formatCountsMap[format] ?: 0L) + 1L
        }

        return formatCountsMap
            .filter { (fmt, count) -> count > 0L || criteria.format == fmt }
            .map { (fmt, count) -> FacetCountResponse(fmt.name, count) }
    }

    /**
     * Counts artifacts per repository, over GitHub and Bitbucket alike.
     *
     * A repository is offered as `owner/repo` (GitHub) or `workspace/slug` (Bitbucket). The owner's
     * profile artifact (the GitHub org, the Bitbucket workspace) names no repository but belongs to
     * every chosen repository of its owner, so its count is added to each repository of the same
     * system; owners of different systems are kept apart even when they share a name. When both
     * systems have a repository under the same `a/b` name, the two are one option and their counts add
     * up, which is exactly what the repository filter returns for that value.
     */
    private fun computeRepositoryFacets(projectId: UUID, criteria: ArtifactFilterCriteria): List<FacetCountResponse> {
        val rows = facetRows(projectId, criteria, FacetKind.REPOSITORIES) { _, root ->
            val system = root.get<SourceSystem>("sourceSystem")
            FacetQuery(
                columns = listOf(system, root.get<String>("sourceId"), root.get<ArtifactType>("artifactType")),
                conditions = listOf(system.`in`(REPOSITORY_SOURCE_SYSTEMS)),
            )
        }
        val counts = RepositoryCounts()
        for (row in rows) {
            counts.add(row[0] as SourceSystem, row[1] as String, row[2] as ArtifactType)
        }
        return counts.facets(criteria.repositories)
    }

    /**
     * Counts artifacts per language under every active filter except the language one.
     *
     * Groups by lower(language) so the counts use the same case folding as the filter predicate;
     * min(language) then picks one stored spelling to display for the group.
     */
    private fun computeLanguageFacets(projectId: UUID, criteria: ArtifactFilterCriteria): List<FacetCountResponse> {
        val rows = facetRows(projectId, criteria, FacetKind.LANGUAGES) { cb, root ->
            val language = root.get<String>("language")
            FacetQuery(
                columns = listOf(cb.least(language), cb.countDistinct(root.get<UUID>("id"))),
                conditions = listOf(cb.isNotNull(language)),
                groupBy = cb.lower(language),
            )
        }
        val counted = rows.map { row ->
            (row[0] as String) to (row[1] as Number).toLong()
        }
        return languageFacetOptions(counted, criteria.selectedLanguages())
    }
}

/** How many artifacts each repository holds, with the artifacts of its organisation counted into it. */
private class RepositoryCounts {
    private val repoCountsBySystem = mutableMapOf<Pair<SourceSystem, String>, Long>()
    private val orgCountsBySystem = mutableMapOf<Pair<SourceSystem, String>, Long>()

    fun add(system: SourceSystem, sourceId: String, artifactType: ArtifactType) {
        if (artifactType == ArtifactType.ORG_METADATA) {
            val owner = ArtifactFacetRepositoryImpl.extractOwnerFromOrgSourceId(system, sourceId)
            if (owner != null) {
                val key = system to owner
                orgCountsBySystem[key] = (orgCountsBySystem[key] ?: 0L) + 1L
            }
        } else {
            val repo = ArtifactFacetRepositoryImpl.extractRepositoryFromSourceId(system, sourceId)
            if (repo != null) {
                val key = system to repo
                repoCountsBySystem[key] = (repoCountsBySystem[key] ?: 0L) + 1L
            }
        }
    }

    /** The facet entries, selected repositories always present, sorted by name. */
    fun facets(selected: Set<String>?): List<FacetCountResponse> {
        val repoCountsMap = mutableMapOf<String, Long>()
        for ((key, count) in repoCountsBySystem) {
            val (system, repo) = key
            val owner = repo.substringBefore('/').trim().lowercase()
            val orgCount = orgCountsBySystem[system to owner] ?: 0L
            repoCountsMap[repo] = (repoCountsMap[repo] ?: 0L) + count + orgCount
        }
        selected?.forEach { repo ->
            repoCountsMap.putIfAbsent(repo, 0L)
        }
        return repoCountsMap
            .filter { (repo, count) -> count > 0L || selected?.contains(repo) == true }
            .entries
            .sortedBy { it.key }
            .map { (repo, count) -> FacetCountResponse(repo, count) }
    }
}
