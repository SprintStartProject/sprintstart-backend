package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.FacetCountResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.CompoundSelection
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private enum class FacetKind {
    TYPES,
    SOURCES,
    FORMATS,
    REPOSITORIES,
}

private val SOURCES_EXCLUDED_FACETS = setOf(FacetKind.SOURCES, FacetKind.FORMATS, FacetKind.REPOSITORIES)

@Repository
@Transactional(readOnly = true)
class ArtifactFacetRepositoryImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : ArtifactFacetRepository {
    override fun findProjectArtifactsWithCriteria(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        pageable: Pageable,
    ): Page<ArtifactResponse> {
        val cb = entityManager.criteriaBuilder

        // 1. Data Query (Projection - D3: does not hydrate content TEXT column)
        val query = cb.createQuery(ArtifactResponse::class.java)
        val root = query.from(Artifact::class.java)
        val projectJoin = root.join<Artifact, UUID>("projectIdsInternal")

        query.select(artifactProjection(cb, root))

        val predicates = buildPredicates(cb, root, projectJoin, projectId, criteria, null)
        query.where(*predicates.toTypedArray())

        // D2: Deterministic sort (ingestedAt DESC, id ASC)
        query.orderBy(
            cb.desc(root.get<Instant>("ingestedAt")),
            cb.asc(root.get<UUID>("id")),
        )

        val typedQuery = entityManager.createQuery(query)
        typedQuery.firstResult = pageable.offset.toInt()
        typedQuery.maxResults = pageable.pageSize
        val items = typedQuery.resultList

        // 2. Count Query
        val countQuery = cb.createQuery(Long::class.java)
        val countRoot = countQuery.from(Artifact::class.java)
        val countJoin = countRoot.join<Artifact, UUID>("projectIdsInternal")

        countQuery.select(cb.countDistinct(countRoot.get<UUID>("id")))
        val countPredicates = buildPredicates(cb, countRoot, countJoin, projectId, criteria, null)
        countQuery.where(*countPredicates.toTypedArray())

        val totalElements = entityManager.createQuery(countQuery).singleResult ?: 0L

        return PageImpl(items, pageable, totalElements)
    }

    override fun findProjectArtifactById(
        projectId: UUID,
        artifactId: UUID,
    ): ArtifactResponse? {
        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(ArtifactResponse::class.java)
        val root = query.from(Artifact::class.java)
        val projectJoin = root.join<Artifact, UUID>("projectIdsInternal")

        query.select(artifactProjection(cb, root))
        query.where(
            cb.equal(root.get<UUID>("id"), artifactId),
            cb.equal(projectJoin, projectId),
        )

        return entityManager
            .createQuery(query)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
    }

    /**
     * The response projection shared by every artifact read.
     *
     * Deliberately not the entity: `Artifact` carries the eagerly fetched `content` TEXT column,
     * so hydrating it to answer a metadata question drags whole file bodies across JDBC. Keep the
     * field list here and nowhere else — [ArtifactResponse] is the only shape either query builds.
     */
    private fun artifactProjection(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
    ): CompoundSelection<ArtifactResponse> = cb.construct(
        ArtifactResponse::class.java,
        root.get<UUID>("id"),
        root.get<String?>("title"),
        root.get<SourceSystem>("sourceSystem"),
        root.get<String>("sourceId"),
        root.get<String?>("sourceUrl"),
        root.get<ArtifactType>("artifactType"),
        root.get<Instant>("ingestedAt"),
        root.get<Instant?>("lastChangedAt"),
        root.get<String>("metadata"),
        root.get<String?>("sourceVersion"),
    )

    override fun findFacets(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): ArtifactFacetsResponse {
        val cb = entityManager.criteriaBuilder
        return ArtifactFacetsResponse(
            types = computeTypeFacets(cb, projectId, criteria),
            sources = computeSourceFacets(cb, projectId, criteria),
            formats = computeFormatFacets(cb, projectId, criteria),
            repositories = computeRepositoryFacets(cb, projectId, criteria),
        )
    }

    private fun computeTypeFacets(
        cb: CriteriaBuilder,
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): List<FacetCountResponse> {
        val typeCountsMap = mutableMapOf<ArtifactType, Long>()
        val query = cb.createQuery(Array<Any>::class.java)
        val root = query.from(Artifact::class.java)
        val join = root.join<Artifact, UUID>("projectIdsInternal")
        query.multiselect(
            root.get<ArtifactType>("artifactType"),
            cb.countDistinct(root.get<UUID>("id")),
        )
        val preds = buildPredicates(cb, root, join, projectId, criteria, FacetKind.TYPES)
        query.where(*preds.toTypedArray())
        query.groupBy(root.get<ArtifactType>("artifactType"))

        for (row in entityManager.createQuery(query).resultList) {
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

    private fun computeSourceFacets(
        cb: CriteriaBuilder,
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): List<FacetCountResponse> {
        val sourceCountsMap = mutableMapOf<SourceSystem, Long>()
        val query = cb.createQuery(Array<Any>::class.java)
        val root = query.from(Artifact::class.java)
        val join = root.join<Artifact, UUID>("projectIdsInternal")
        query.multiselect(
            root.get<SourceSystem>("sourceSystem"),
            cb.countDistinct(root.get<UUID>("id")),
        )
        val preds = buildPredicates(cb, root, join, projectId, criteria, FacetKind.SOURCES)
        query.where(*preds.toTypedArray())
        query.groupBy(root.get<SourceSystem>("sourceSystem"))

        for (row in entityManager.createQuery(query).resultList) {
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

    private fun computeFormatFacets(
        cb: CriteriaBuilder,
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): List<FacetCountResponse> {
        val formatCountsMap = mutableMapOf(
            UploadFormat.PDF to 0L,
            UploadFormat.MARKDOWN to 0L,
            UploadFormat.IMAGE to 0L,
            UploadFormat.OTHER to 0L,
        )
        val query = cb.createQuery(Array<Any>::class.java)
        val root = query.from(Artifact::class.java)
        val join = root.join<Artifact, UUID>("projectIdsInternal")
        query.multiselect(
            root.get<String?>("title"),
            root.get<String?>("sourceUrl"),
            root.get<String>("sourceId"),
            root.get<String?>("mime"),
            root.get<String?>("language"),
        )
        val preds = buildPredicates(cb, root, join, projectId, criteria, FacetKind.FORMATS).toMutableList()
        preds.add(cb.equal(root.get<SourceSystem>("sourceSystem"), SourceSystem.UPLOAD))
        query.where(*preds.toTypedArray())

        for (row in entityManager.createQuery(query).resultList) {
            val title = row[0] as? String
            val sourceUrl = row[1] as? String
            val sourceId = row[2] as String
            val mime = row[3] as? String
            val language = row[4] as? String
            val format = classifyUploadFormat(title, sourceUrl, sourceId, mime, language)
            formatCountsMap[format] = (formatCountsMap[format] ?: 0L) + 1L
        }

        return formatCountsMap
            .filter { (fmt, count) -> count > 0L || criteria.format == fmt }
            .map { (fmt, count) -> FacetCountResponse(fmt.name, count) }
    }

    private fun computeRepositoryFacets(
        cb: CriteriaBuilder,
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): List<FacetCountResponse> {
        val repoCountsMap = mutableMapOf<String, Long>()
        val orgCountsMap = mutableMapOf<String, Long>()
        val query = cb.createQuery(Array<Any>::class.java)
        val root = query.from(Artifact::class.java)
        val join = root.join<Artifact, UUID>("projectIdsInternal")
        query.multiselect(
            root.get<String>("sourceId"),
            root.get<ArtifactType>("artifactType"),
        )
        val preds = buildPredicates(cb, root, join, projectId, criteria, FacetKind.REPOSITORIES).toMutableList()
        preds.add(cb.equal(root.get<SourceSystem>("sourceSystem"), SourceSystem.GITHUB))
        query.where(*preds.toTypedArray())

        for (row in entityManager.createQuery(query).resultList) {
            val sourceId = row[0] as String
            val artifactType = row[1] as ArtifactType

            if (artifactType == ArtifactType.ORG_METADATA) {
                val orgLogin = sourceId.trim().lowercase()
                orgCountsMap[orgLogin] = (orgCountsMap[orgLogin] ?: 0L) + 1L
            } else {
                val repo = extractRepositoryFromSourceId(sourceId)
                if (repo != null) {
                    repoCountsMap[repo] = (repoCountsMap[repo] ?: 0L) + 1L
                }
            }
        }
        for (repo in repoCountsMap.keys.toList()) {
            val owner = repo.substringBefore('/').trim().lowercase()
            val orgCount = orgCountsMap[owner] ?: 0L
            if (orgCount > 0L) {
                repoCountsMap[repo] = (repoCountsMap[repo] ?: 0L) + orgCount
            }
        }
        criteria.repositories?.forEach { repo ->
            repoCountsMap.putIfAbsent(repo, 0L)
        }
        return repoCountsMap
            .filter { (repo, count) -> count > 0L || criteria.repositories?.contains(repo) == true }
            .entries
            .sortedBy { it.key }
            .map { (repo, count) -> FacetCountResponse(repo, count) }
    }

    private fun buildPredicates(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        projectJoin: Join<Artifact, UUID>,
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        exclude: FacetKind?,
    ): List<Predicate> {
        val predicates = mutableListOf<Predicate>()
        predicates.add(cb.equal(projectJoin, projectId))

        if (!criteria.search.isNullOrBlank()) {
            val pattern = "%${criteria.search.trim().lowercase()}%"
            val titleMatch = cb.like(cb.lower(root.get("title")), pattern)
            val sourceIdMatch = cb.like(cb.lower(root.get("sourceId")), pattern)
            val sourceUrlMatch = cb.like(cb.lower(root.get("sourceUrl")), pattern)
            predicates.add(cb.or(titleMatch, sourceIdMatch, sourceUrlMatch))
        }

        if (exclude != FacetKind.TYPES && !criteria.types.isNullOrEmpty()) {
            predicates.add(root.get<ArtifactType>("artifactType").`in`(criteria.types))
        }

        if (exclude !in SOURCES_EXCLUDED_FACETS && !criteria.sources.isNullOrEmpty()) {
            predicates.add(root.get<SourceSystem>("sourceSystem").`in`(criteria.sources))
        }

        if (exclude != FacetKind.FORMATS && criteria.format != null) {
            val notUpload = cb.notEqual(root.get<SourceSystem>("sourceSystem"), SourceSystem.UPLOAD)
            val uploadMatchesFormat = buildUploadFormatPredicate(cb, root, criteria.format)
            predicates.add(cb.or(notUpload, uploadMatchesFormat))
        }

        if (exclude != FacetKind.REPOSITORIES && !criteria.repositories.isNullOrEmpty()) {
            val notGithub = cb.notEqual(root.get<SourceSystem>("sourceSystem"), SourceSystem.GITHUB)
            val githubMatchesRepo = buildGithubRepoPredicate(cb, root, criteria.repositories)
            predicates.add(cb.or(notGithub, githubMatchesRepo))
        }

        return predicates
    }

    private fun buildUploadFormatPredicate(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        format: UploadFormat,
    ): Predicate {
        // Nullable columns fold to "" exactly as `classifyUploadFormat` does, so this predicate
        // and the Kotlin classifier that produces the facet counts can never disagree. Without
        // the coalesce, `NOT(OR(...))` — the OTHER bucket — evaluates to NULL rather than TRUE
        // for a row whose mime, language and title are unset, and an upload the facet counts as
        // OTHER would come back from the filter as nothing at all.
        val titleLower = cb.coalesce(cb.lower(root.get("title")), "")
        val sourceUrlLower = cb.coalesce(cb.lower(root.get("sourceUrl")), "")
        val sourceIdLower = cb.lower(root.get("sourceId"))
        val mimeLower = cb.coalesce(cb.lower(root.get("mime")), "")
        val languageLower = cb.coalesce(cb.lower(root.get("language")), "")

        val isPdf = cb.or(
            cb.equal(mimeLower, "application/pdf"),
            cb.like(titleLower, "%.pdf"),
            cb.like(sourceUrlLower, "%.pdf"),
            cb.like(sourceIdLower, "%.pdf"),
        )

        val isMarkdown = cb.or(
            languageLower.`in`("markdown", "md"),
            cb.like(mimeLower, "%markdown%"),
            cb.like(titleLower, "%.md"),
            cb.like(titleLower, "%.markdown"),
            cb.like(sourceUrlLower, "%.md"),
            cb.like(sourceUrlLower, "%.markdown"),
            cb.like(sourceIdLower, "%.md"),
            cb.like(sourceIdLower, "%.markdown"),
        )

        val imageExtPredicates = IMAGE_EXTENSIONS.flatMap { ext ->
            listOf(cb.like(titleLower, "%$ext"), cb.like(sourceUrlLower, "%$ext"))
        }
        val isImage = cb.or(
            cb.like(mimeLower, "image/%"),
            *imageExtPredicates.toTypedArray(),
        )

        return when (format) {
            UploadFormat.PDF -> isPdf
            UploadFormat.MARKDOWN -> isMarkdown
            UploadFormat.IMAGE -> isImage
            UploadFormat.OTHER -> cb.not(cb.or(isPdf, isMarkdown, isImage))
        }
    }

    private fun buildGithubRepoPredicate(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        repositories: Set<String>,
    ): Predicate {
        val sourceId = root.get<String>("sourceId")
        val artifactType = root.get<ArtifactType>("artifactType")

        val repoPrefixPredicates = repositories.map { repo ->
            cb.like(sourceId, "github:$repo:%")
        }
        val isNonOrgRepoMatch = cb.and(
            cb.notEqual(artifactType, ArtifactType.ORG_METADATA),
            cb.or(*repoPrefixPredicates.toTypedArray()),
        )

        val owners = repositories.map { it.substringBefore('/').trim().lowercase() }.toSet()
        val isOrgMatch = cb.and(
            cb.equal(artifactType, ArtifactType.ORG_METADATA),
            cb.lower(sourceId).`in`(owners),
        )

        return cb.or(isNonOrgRepoMatch, isOrgMatch)
    }

    companion object {
        private val IMAGE_EXTENSIONS = listOf(
            ".png",
            ".jpg",
            ".jpeg",
            ".gif",
            ".webp",
            ".svg",
            ".bmp",
            ".avif",
        )

        fun extractRepositoryFromSourceId(sourceId: String): String? {
            if (!sourceId.startsWith("github:")) return null
            val parts = sourceId.split(':')
            return if (parts.size >= 3) parts[1] else null
        }

        private fun isPdf(title: String, url: String, id: String, mime: String): Boolean =
            mime == "application/pdf" || title.endsWith(".pdf") || url.endsWith(".pdf") || id.endsWith(".pdf")

        private fun isMarkdown(title: String, url: String, id: String, mime: String, lang: String): Boolean =
            lang in listOf("markdown", "md") ||
                mime.contains("markdown") ||
                title.endsWith(".md") ||
                title.endsWith(".markdown") ||
                url.endsWith(".md") ||
                url.endsWith(".markdown") ||
                id.endsWith(".md") ||
                id.endsWith(".markdown")

        private fun isImage(title: String, url: String, mime: String): Boolean =
            mime.startsWith("image/") || IMAGE_EXTENSIONS.any { title.endsWith(it) || url.endsWith(it) }

        fun classifyUploadFormat(
            title: String?,
            sourceUrl: String?,
            sourceId: String,
            mime: String?,
            language: String?,
        ): UploadFormat {
            val titleLower = title?.lowercase() ?: ""
            val sourceUrlLower = sourceUrl?.lowercase() ?: ""
            val sourceIdLower = sourceId.lowercase()
            val mimeLower = mime?.lowercase() ?: ""
            val languageLower = language?.lowercase() ?: ""

            return when {
                isPdf(titleLower, sourceUrlLower, sourceIdLower, mimeLower) -> UploadFormat.PDF
                isMarkdown(titleLower, sourceUrlLower, sourceIdLower, mimeLower, languageLower) -> UploadFormat.MARKDOWN
                isImage(titleLower, sourceUrlLower, mimeLower) -> UploadFormat.IMAGE
                else -> UploadFormat.OTHER
            }
        }
    }
}
