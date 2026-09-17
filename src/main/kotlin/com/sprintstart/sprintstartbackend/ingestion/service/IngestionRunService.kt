package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ConfluenceConnectionApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.IngestionRunPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.PageMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Reads ingestion runs for API consumers.
 *
 * This service keeps pagination, filtering, and response mapping out of the controller so the API
 * surface can stay stable even if the persistence model grows additional run metadata later.
 */
@Service
class IngestionRunService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val githubRepositoryApi: GithubRepositoryApi,
    private val jiraInstanceApi: JiraInstanceApi,
    private val confluenceConnectionApi: ConfluenceConnectionApi,
) {
    /**
     * Returns the newest ingestion runs first.
     *
     * @param limit maximum number of runs returned from the first page of run history
     * @return API-ready run summaries including counters and failed items
     * @throws IllegalArgumentException If Spring Data rejects the requested page size.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving recent ingestion runs")
    fun getRecentRuns(
        limit: Int = 10,
    ): List<IngestionRunResponse> =
        ingestionRunRepository
            .findByOrderByStartedAtDesc(
                PageRequest.of(0, limit),
            ).map { it.toResponse() }

    /**
     * Returns a single ingestion run by id, including its full source-instance metadata.
     *
     * @param runId The ingestion run to load.
     * @return The API representation of the run.
     * @throws ResponseStatusException `404` when no run exists for the given id.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving single ingestion run")
    fun getRun(runId: UUID): IngestionRunResponse =
        ingestionRunRepository.findByIdOrNull(runId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion run with id $runId not found")

    /**
     * Searches and paginates ingestion runs matching the given filters.
     *
     * All filter parameters are optional and combined with `AND`. The result is ordered newest-first
     * by [IngestionRun.startedAt].
     *
     * @param page 1-based page index.
     * @param size Page size.
     * @param sourceSystem Optional source-system filter (e.g. GITHUB, JIRA).
     * @param repositoryId Optional GitHub repository filter.
     * @param sourceRef Optional connector-neutral source reference filter (for Jira the instance URL).
     * @param projectId Optional project filter, resolved via the project's connected repositories,
     * Jira instances, Confluence connections, and uploaded artifacts.
     * @param status Optional run-status filter.
     * @param since Optional lower bound (inclusive) on the run start time.
     * @return One page of runs together with pagination metadata.
     * @throws IllegalArgumentException when Spring Data rejects the requested page or page size.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving filtered ingestion runs page")
    @Suppress("LongParameterList")
    fun getRuns(
        page: Int,
        size: Int,
        sourceSystem: SourceSystem? = null,
        repositoryId: UUID? = null,
        sourceRef: String? = null,
        projectId: UUID? = null,
        status: IngestionRunStatus? = null,
        since: Instant? = null,
    ): IngestionRunPageResponse {
        val projectSources: ProjectSources? = projectId?.let { resolveProjectSources(it) }

        val specification =
            Specification<IngestionRun> { root, _, cb ->
                val predicates = mutableListOf<Predicate>()
                sourceSystem?.let { predicates.add(cb.equal(root.get<SourceSystem>("sourceSystem"), it)) }
                repositoryId?.let { predicates.add(cb.equal(root.get<UUID>("sourceInstanceId"), it)) }
                sourceRef?.let { predicates.add(cb.equal(root.get<String>("sourceInstanceRef"), it)) }
                status?.let { predicates.add(cb.equal(root.get<IngestionRunStatus>("status"), it)) }
                since?.let { predicates.add(cb.greaterThanOrEqualTo(root.get<Instant>("startedAt"), it)) }
                projectId?.let { pId ->
                    predicates.add(buildProjectPredicate(root, cb, pId, projectSources))
                }
                if (predicates.isEmpty()) null else cb.and(*predicates.toTypedArray())
            }

        val pageable = PageRequest.of(page - 1, size, Sort.by("startedAt").descending())
        val result: Page<IngestionRun> = ingestionRunRepository.findAll(specification, pageable)

        return IngestionRunPageResponse(
            items = result.content.map { it.toResponse() },
            page = PageMetadata(
                number = page.toLong(),
                size = size.toLong(),
                totalElements = result.totalElements,
                totalPages = result.totalPages.toLong(),
                hasNext = result.hasNext(),
                hasPrevious = result.hasPrevious(),
            ),
        )
    }

    private fun buildProjectPredicate(
        root: Root<IngestionRun>,
        cb: CriteriaBuilder,
        projectId: UUID,
        projectSources: ProjectSources?,
    ): Predicate {
        val sources = projectSources ?: resolveProjectSources(projectId)
        val matches = buildList {
            if (sources.repositoryIds.isNotEmpty()) {
                add(sourceInstanceIdPredicate(root, cb, SourceSystem.GITHUB, sources.repositoryIds))
            }
            if (sources.jiraRefs.isNotEmpty()) {
                add(
                    cb.and(
                        cb.equal(root.get<SourceSystem>("sourceSystem"), SourceSystem.JIRA),
                        root.get<String>("sourceInstanceRef").`in`(sources.jiraRefs),
                    ),
                )
            }
            if (sources.confluenceConnectionIds.isNotEmpty()) {
                add(sourceInstanceIdPredicate(root, cb, SourceSystem.CONFLUENCE, sources.confluenceConnectionIds))
            }
            add(
                cb.and(
                    cb.equal(root.get<SourceSystem>("sourceSystem"), SourceSystem.UPLOAD),
                    cb.equal(root.get<UUID>("sourceInstanceId"), projectId),
                ),
            )
        }
        return when (matches.size) {
            0 -> cb.disjunction()
            1 -> matches.single()
            else -> cb.or(*matches.toTypedArray())
        }
    }

    private fun resolveProjectSources(projectId: UUID): ProjectSources =
        ProjectSources(
            repositoryIds = githubRepositoryApi.getRepositoryIdsByProject(projectId),
            jiraRefs = jiraInstanceApi.getInstanceRefsByProject(projectId),
            confluenceConnectionIds = confluenceConnectionApi.getConnectionIdsByProject(projectId),
        )

    private fun sourceInstanceIdPredicate(
        root: Root<IngestionRun>,
        cb: CriteriaBuilder,
        sourceSystem: SourceSystem,
        sourceInstanceIds: List<UUID>,
    ): Predicate {
        return cb.and(
            cb.equal(root.get<SourceSystem>("sourceSystem"), sourceSystem),
            root.get<UUID>("sourceInstanceId").`in`(sourceInstanceIds),
        )
    }

    private data class ProjectSources(
        val repositoryIds: List<UUID>,
        val jiraRefs: List<String>,
        val confluenceConnectionIds: List<UUID>,
    )
}

/**
 * Maps an ingestion run entity to its API representation.
 *
 * The persisted source-instance reference is connector-neutral, so the GitHub-specific response
 * fields (`owner`, `name`, `repositoryId`) are derived here for GITHUB runs by splitting the
 * denormalized `sourceInstanceRef` ("owner/name"). This keeps the API response shape stable for the
 * frontend while the entity stays abstract across connectors.
 */
internal fun IngestionRun.toResponse(): IngestionRunResponse {
    val isGithub = sourceSystem == SourceSystem.GITHUB && sourceInstanceRef != null
    return IngestionRunResponse(
        runId = id,
        sourceSystem = sourceSystem,
        sourceId = sourceInstanceRef,
        owner = if (isGithub) sourceInstanceRef!!.substringBefore("/") else null,
        name = if (isGithub) sourceInstanceRef!!.substringAfter("/") else null,
        repositoryId = sourceInstanceId,
        startedAt = startedAt,
        finishedAt = finishedAt,
        ingestedCount = ingestedCount,
        updatedCount = updatedCount,
        deletedCount = deletedCount,
        failedCount = failedCount,
        failedItems = failedItems,
        status = status,
        failureReason = failureReason,
        aiSyncStatus = aiSyncStatus,
        aiSyncFailureReason = aiSyncFailureReason,
    )
}
