package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal enum class FacetKind {
    TYPES,
    SOURCES,
    FORMATS,
    REPOSITORIES,
    LANGUAGES,
}

private val SOURCES_EXCLUDED_FACETS = setOf(FacetKind.SOURCES, FacetKind.FORMATS, FacetKind.REPOSITORIES)

/** The source systems whose artifacts belong to a repository and therefore feed the repository facet. */
internal val REPOSITORY_SOURCE_SYSTEMS = listOf(SourceSystem.GITHUB, SourceSystem.BITBUCKET)

/** Selected languages, trimmed, blanks dropped, deduplicated ignoring case. */
internal fun ArtifactFilterCriteria.selectedLanguages(): List<String> =
    languages
        .orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

private fun typesFacet(root: Root<Artifact>, criteria: ArtifactFilterCriteria, exclude: FacetKind?): Predicate? {
    if (exclude == FacetKind.TYPES || criteria.types.isNullOrEmpty()) return null
    return root.get<ArtifactType>("artifactType").`in`(criteria.types)
}

private fun sourcesFacet(root: Root<Artifact>, criteria: ArtifactFilterCriteria, exclude: FacetKind?): Predicate? {
    if (exclude in SOURCES_EXCLUDED_FACETS || criteria.sources.isNullOrEmpty()) return null
    return root.get<SourceSystem>("sourceSystem").`in`(criteria.sources)
}

private fun languagesFacet(
    cb: CriteriaBuilder,
    root: Root<Artifact>,
    criteria: ArtifactFilterCriteria,
    exclude: FacetKind?,
): Predicate? {
    val languages = criteria.selectedLanguages()
    if (exclude == FacetKind.LANGUAGES || languages.isEmpty()) return null
    return cb.lower(root.get<String>("language")).`in`(languages.map { it.lowercase() })
}

/**
 * The WHERE clause of every artifact query: the project, the search text and each active filter.
 *
 * Pure over the criteria builder, so the list and every facet count build the same conditions.
 */
internal object ArtifactFilterPredicates {
    fun predicates(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        projectJoin: Join<Artifact, UUID>,
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        exclude: FacetKind?,
    ): List<Predicate> {
        // No facet counts the activity date, so the window applies to every query alike -- which is
        // what keeps facet counts equal to the list's totalElements under the same filter.
        return listOfNotNull(
            cb.equal(projectJoin, projectId),
            searchPredicate(cb, root, criteria.search),
            typesFacet(root, criteria, exclude),
            sourcesFacet(root, criteria, exclude),
            formatFacet(cb, root, criteria, exclude),
            repositoriesFacet(cb, root, criteria, exclude),
            languagesFacet(cb, root, criteria, exclude),
        ) + buildActivityWindowPredicates(cb, root, criteria.from, criteria.to)
    }

    private fun formatFacet(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        criteria: ArtifactFilterCriteria,
        exclude: FacetKind?,
    ): Predicate? {
        if (exclude == FacetKind.FORMATS || criteria.format == null) return null
        val notUpload = cb.notEqual(root.get<SourceSystem>("sourceSystem"), SourceSystem.UPLOAD)
        return cb.or(notUpload, buildUploadFormatPredicate(cb, root, criteria.format))
    }

    private fun repositoriesFacet(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        criteria: ArtifactFilterCriteria,
        exclude: FacetKind?,
    ): Predicate? {
        if (exclude == FacetKind.REPOSITORIES || criteria.repositories.isNullOrEmpty()) return null
        return repositoryPredicate(cb, root, criteria.repositories)
    }

    private fun searchPredicate(cb: CriteriaBuilder, root: Root<Artifact>, search: String?): Predicate? {
        if (search.isNullOrBlank()) return null

        val pattern = "%${search.trim().lowercase()}%"
        val titleMatch = cb.like(cb.lower(root.get("title")), pattern)
        val sourceIdMatch = cb.like(cb.lower(root.get("sourceId")), pattern)
        val sourceUrlMatch = cb.like(cb.lower(root.get("sourceUrl")), pattern)
        return cb.or(titleMatch, sourceIdMatch, sourceUrlMatch)
    }

    private fun repositoryPredicate(cb: CriteriaBuilder, root: Root<Artifact>, repositories: Set<String>): Predicate {
        val sourceSystem = root.get<SourceSystem>("sourceSystem")
        // Artifacts of other systems are outside the repository facet's reach and always pass;
        // within GitHub and Bitbucket the selection narrows strictly, each by its own source ids.
        val outsideFacet = cb.not(sourceSystem.`in`(REPOSITORY_SOURCE_SYSTEMS))
        val githubMatchesRepo = cb.and(
            cb.equal(sourceSystem, SourceSystem.GITHUB),
            buildGithubRepoPredicate(cb, root, repositories),
        )
        val bitbucketMatchesRepo = cb.and(
            cb.equal(sourceSystem, SourceSystem.BITBUCKET),
            buildBitbucketRepoPredicate(cb, root, repositories),
        )
        return cb.or(outsideFacet, githubMatchesRepo, bitbucketMatchesRepo)
    }

    /**
     * Restricts an artifact's last activity to the inclusive UTC calendar-day window `[from, to]`.
     *
     * Activity is `COALESCE(lastChangedAt, ingestedAt)`: the last content change, or the import for
     * an artifact that never changed. It is the same key `CHANGED_DESC` sorts by, so "changed in
     * the last 7 days" and "most recently changed first" always agree. An artifact imported long
     * ago but edited inside the window matches; one imported inside the window and changed after
     * it does not, because its latest activity lies outside.
     *
     * The end bound is `< start of the day after [to]` rather than `<= end of [to]`, so activity
     * in the last microsecond of that day still matches, whatever precision the column keeps.
     */
    private fun buildActivityWindowPredicates(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        from: LocalDate?,
        to: LocalDate?,
    ): List<Predicate> {
        val activityAt = cb.coalesce(root.get<Instant>("lastChangedAt"), root.get<Instant>("ingestedAt"))
        return listOfNotNull(
            from?.let { cb.greaterThanOrEqualTo(activityAt, it.atStartOfDay(ZoneOffset.UTC).toInstant()) },
            to?.let { cb.lessThan(activityAt, it.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()) },
        )
    }

    private fun buildUploadFormatPredicate(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        format: UploadFormat,
    ): Predicate {
        val columns = UploadColumns(cb, root)
        val isPdf = columns.isPdf()
        val isMarkdown = columns.isMarkdown()

        return when (format) {
            UploadFormat.PDF -> isPdf
            UploadFormat.MARKDOWN -> cb.and(cb.not(isPdf), isMarkdown)
            UploadFormat.IMAGE -> cb.and(cb.not(cb.or(isPdf, isMarkdown)), columns.isImage())
            UploadFormat.OTHER -> cb.not(cb.or(isPdf, isMarkdown, columns.isImage()))
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
            val escapedPrefix = escapeLikeLiteral("github:$repo:")
            cb.like(sourceId, "$escapedPrefix%", LIKE_ESCAPE.single())
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

    /**
     * Matches the Bitbucket artifacts of the chosen `workspace/slug` repositories, plus the profile of
     * each workspace that owns one of them.
     *
     * The repository half is a literal prefix match on `bitbucket:workspace/slug:`, escaped the same
     * way as the GitHub one so an underscore in a slug is not a wildcard. The workspace profile's
     * source id is `bitbucket:<workspace>:ORG_METADATA`, which that prefix cannot match, so it is
     * compared by its whole lower-cased value instead.
     */
    private fun buildBitbucketRepoPredicate(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        repositories: Set<String>,
    ): Predicate {
        val sourceId = root.get<String>("sourceId")
        val artifactType = root.get<ArtifactType>("artifactType")

        val repoPrefixPredicates = repositories.map { repo ->
            val escapedPrefix = escapeLikeLiteral("bitbucket:$repo:")
            cb.like(sourceId, "$escapedPrefix%", LIKE_ESCAPE.single())
        }
        val isNonOrgRepoMatch = cb.and(
            cb.notEqual(artifactType, ArtifactType.ORG_METADATA),
            cb.or(*repoPrefixPredicates.toTypedArray()),
        )

        val workspaceSourceIds = repositories
            .map { "bitbucket:${it.substringBefore('/').trim().lowercase()}:${ArtifactType.ORG_METADATA}".lowercase() }
            .toSet()
        val isWorkspaceMatch = cb.and(
            cb.equal(artifactType, ArtifactType.ORG_METADATA),
            cb.lower(sourceId).`in`(workspaceSourceIds),
        )

        return cb.or(isNonOrgRepoMatch, isWorkspaceMatch)
    }
}

/**
 * The columns an upload's format is read from, folded to lower case.
 *
 * Nullable columns fold to "" exactly as `classifyUploadFormat` does. Without the coalesce,
 * `NOT(OR(...))` is NULL for a row with unset columns, and the OTHER bucket would match nothing.
 */
private class UploadColumns(
    private val cb: CriteriaBuilder,
    root: Root<Artifact>,
) {
    private val titleLower = cb.coalesce(cb.lower(root.get("title")), "")
    private val sourceUrlLower = cb.coalesce(cb.lower(root.get("sourceUrl")), "")
    private val sourceIdLower = cb.lower(root.get("sourceId"))
    private val mimeLower = cb.coalesce(cb.lower(root.get("mime")), "")
    private val languageLower = cb.coalesce(cb.lower(root.get("language")), "")

    fun isPdf(): Predicate =
        cb.or(
            cb.equal(mimeLower, "application/pdf"),
            cb.like(titleLower, "%.pdf"),
            cb.like(sourceUrlLower, "%.pdf"),
            cb.like(sourceIdLower, "%.pdf"),
        )

    fun isMarkdown(): Predicate =
        cb.or(
            languageLower.`in`("markdown", "md"),
            cb.like(mimeLower, "%markdown%"),
            cb.like(titleLower, "%.md"),
            cb.like(titleLower, "%.markdown"),
            cb.like(sourceUrlLower, "%.md"),
            cb.like(sourceUrlLower, "%.markdown"),
            cb.like(sourceIdLower, "%.md"),
            cb.like(sourceIdLower, "%.markdown"),
        )

    fun isImage(): Predicate {
        val imageExtPredicates = ArtifactFacetRepositoryImpl.IMAGE_EXTENSIONS.flatMap { ext ->
            listOf(cb.like(titleLower, "%$ext"), cb.like(sourceUrlLower, "%$ext"))
        }
        return imageExtPredicates.fold(cb.like(mimeLower, "image/%")) { any, extension -> cb.or(any, extension) }
    }
}
