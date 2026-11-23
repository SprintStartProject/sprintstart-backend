package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSort
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.FacetCountResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.CompoundSelection
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Nulls
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Lower-cased language names the facet never offers: the format facet covers documents. */
private val DOCUMENT_LANGUAGES = setOf("markdown", "plain text")

/**
 * Shapes counted `(display name, count)` language groups into facet options.
 *
 * Document languages are dropped unless selected; a selected language with no match is added at
 * count 0, so its chip stays visible. Ordered by count descending, then value ascending.
 */
internal fun languageFacetOptions(
    counted: List<Pair<String, Long>>,
    selected: List<String>,
): List<FacetCountResponse> {
    val selectedLower = selected.map { it.lowercase() }.toSet()
    val countedLower = counted.map { it.first.lowercase() }.toSet()
    val shown = counted.filter { (value, _) ->
        value.lowercase() !in DOCUMENT_LANGUAGES || value.lowercase() in selectedLower
    }
    val unmatched = selected.filter { it.lowercase() !in countedLower }.map { it to 0L }
    return (shown + unmatched)
        .map { (value, count) -> FacetCountResponse(value, count) }
        .sortedWith(compareByDescending<FacetCountResponse> { it.count }.thenBy { it.value })
}

@Repository
@Transactional(readOnly = true)
@Suppress("TooManyFunctions")
class ArtifactFacetRepositoryImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : ArtifactFacetRepository {
    private val facetCounter = ArtifactFacetCounter(entityManager)

    override fun findProjectArtifactsWithCriteria(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        sort: ArtifactSort,
        pageable: Pageable,
    ): Page<ArtifactResponse> {
        val cb = entityManager.criteriaBuilder

        val query = cb.createQuery(ArtifactResponse::class.java)
        val root = query.from(Artifact::class.java)
        val projectJoin = root.join<Artifact, UUID>("projectIdsInternal")

        query.select(artifactProjection(cb, root))

        val predicates = ArtifactFilterPredicates.predicates(cb, root, projectJoin, projectId, criteria, null)
        query.where(*predicates.toTypedArray())

        query.orderBy(orderFor(cb, root, sort))

        val typedQuery = entityManager.createQuery(query)
        typedQuery.firstResult = pageable.offset.toInt()
        typedQuery.maxResults = pageable.pageSize
        val items = typedQuery.resultList

        val countQuery = cb.createQuery(Long::class.java)
        val countRoot = countQuery.from(Artifact::class.java)
        val countJoin = countRoot.join<Artifact, UUID>("projectIdsInternal")

        countQuery.select(cb.countDistinct(countRoot.get<UUID>("id")))
        val countPredicates = ArtifactFilterPredicates.predicates(cb, countRoot, countJoin, projectId, criteria, null)
        countQuery.where(*countPredicates.toTypedArray())

        val totalElements = entityManager.createQuery(countQuery).singleResult ?: 0L

        return PageImpl(items, pageable, totalElements)
    }

    override fun findFacets(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
    ): ArtifactFacetsResponse = facetCounter.facets(projectId, criteria)

    /**
     * Builds the ORDER BY clause for [sort], always ending in `id ASC`.
     *
     * The id tie-break keeps offset pagination stable: rows sharing the leading key (same
     * import instant, same title, or no title at all) keep one fixed order across pages.
     */
    private fun orderFor(
        cb: CriteriaBuilder,
        root: Root<Artifact>,
        sort: ArtifactSort,
    ): List<Order> {
        val leading = when (sort) {
            ArtifactSort.ADDED_DESC -> cb.desc(root.get<Instant>("ingestedAt"))
            ArtifactSort.CHANGED_DESC -> cb.desc(
                cb.coalesce(root.get<Instant>("lastChangedAt"), root.get<Instant>("ingestedAt")),
            )
            // Nulls.LAST is spelled out: databases disagree on where NULL sorts by default.
            ArtifactSort.TITLE_ASC -> cb.asc(cb.lower(root.get("title")), Nulls.LAST)
        }
        return listOf(leading, cb.asc(root.get<UUID>("id")))
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
        root.get<String?>("language"),
    )

    companion object {
        internal val IMAGE_EXTENSIONS = listOf(
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

        /**
         * Reads the `workspace/slug` out of a Bitbucket artifact's source id,
         * `bitbucket:workspace/slug:TYPE:unique`.
         *
         * Returns null for any other prefix and for a middle segment without a slash, which is how a
         * workspace profile (`bitbucket:workspace:ORG_METADATA`) is told apart from a repository
         * artifact.
         */
        fun extractBitbucketRepositoryFromSourceId(sourceId: String): String? {
            if (!sourceId.startsWith("bitbucket:")) return null
            val parts = sourceId.split(':')
            return parts.getOrNull(1)?.takeIf { parts.size >= 3 && it.contains('/') }
        }

        /** Reads the repository out of the source id of a [system] artifact, or null when it has none. */
        internal fun extractRepositoryFromSourceId(
            system: SourceSystem,
            sourceId: String,
        ): String? = when (system) {
            SourceSystem.GITHUB -> extractRepositoryFromSourceId(sourceId)
            SourceSystem.BITBUCKET -> extractBitbucketRepositoryFromSourceId(sourceId)
            else -> null
        }

        /**
         * Reads the lower-cased owner out of the source id of an owner-profile artifact: the GitHub
         * org's source id is its bare login, a Bitbucket workspace's is `bitbucket:workspace:ORG_METADATA`.
         */
        internal fun extractOwnerFromOrgSourceId(
            system: SourceSystem,
            sourceId: String,
        ): String? = when (system) {
            SourceSystem.GITHUB -> sourceId.trim().lowercase()
            SourceSystem.BITBUCKET ->
                sourceId
                    .takeIf { it.startsWith("bitbucket:") }
                    ?.split(':')
                    ?.getOrNull(1)
                    ?.trim()
                    ?.lowercase()
            else -> null
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
            val titleLower = title?.lowercase().orEmpty()
            val sourceUrlLower = sourceUrl?.lowercase().orEmpty()
            val sourceIdLower = sourceId.lowercase()
            val mimeLower = mime?.lowercase().orEmpty()
            val languageLower = language?.lowercase().orEmpty()

            return when {
                isPdf(titleLower, sourceUrlLower, sourceIdLower, mimeLower) -> UploadFormat.PDF
                isMarkdown(titleLower, sourceUrlLower, sourceIdLower, mimeLower, languageLower) -> UploadFormat.MARKDOWN
                isImage(titleLower, sourceUrlLower, mimeLower) -> UploadFormat.IMAGE
                else -> UploadFormat.OTHER
            }
        }
    }
}
