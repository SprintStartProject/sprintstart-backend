package com.sprintstart.sprintstartbackend.ingestion.service

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
import jakarta.persistence.criteria.Predicate
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
     * Returns a filtered, paginated page of ingestion runs, newest first.
     *
     * All filters are optional and combined with AND semantics. `projectId` is resolved to the set
     * of repositories connected to that project and matched against each run's `repositoryId`; a
     * project with no connected repositories yields an empty page.
     *
     * @param page The 1-based page number to return.
     * @param size The maximum number of runs to include in one page.
     * @param sourceSystem Optional source-system filter, for example GITHUB.
     * @param repositoryId Optional connected-repository filter (GitHub, matched on the UUID instance id).
     * @param sourceRef Optional source-instance filter matched on the connector-neutral reference
     * (for Jira the instance URL).
     * @param projectId Optional project filter, resolved via the project's connected repositories and
     * Jira instances.
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
                projectSources?.let { sources ->
                    val matches = buildList {
                        if (sources.repositoryIds.isNotEmpty()) {
                            add(root.get<UUID>("sourceInstanceId").`in`(sources.repositoryIds))
                        }
                        if (sources.jiraRefs.isNotEmpty()) {
                            add(root.get<String>("sourceInstanceRef").`in`(sources.jiraRefs))
                        }
                    }
                    // An empty project (no connected sources at all) matches no run.
                    predicates.add(
                        when (matches.size) {
                            0 -> cb.disjunction()
                            1 -> matches.single()
                            else -> cb.or(matches[0], matches[1])
                        },
                    )
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

    @Transactional(readOnly = true)
    @Tracked("Retrieving ingestion run")
    fun findRunByTransactionId(transactionId: UUID): IngestionRun? =
        ingestionRunRepository.findByIdOrNull(transactionId)

    private fun resolveProjectSources(projectId: UUID): ProjectSources =
        ProjectSources(
            repositoryIds = githubRepositoryApi.getRepositoryIdsByProject(projectId),
            jiraRefs = jiraInstanceApi.getInstanceRefsByProject(projectId),
        )

    private data class ProjectSources(
        val repositoryIds: List<UUID>,
        val jiraRefs: List<String>,
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
