package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.SourceInstanceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Builds the per-connected-source-instance ingestion status view.
 *
 * Where [IngestionStatusService] collapses everything into one aggregate row per source system,
 * this service returns one row per connected GitHub repository: it takes the repository's current
 * connection status and snapshot timestamps (via the GitHub module API) and attaches the counters
 * of that repository's latest ingestion run.
 */
@Service
class IngestionSourceStatusService(
    private val githubRepositoryApi: GithubRepositoryApi,
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Returns one status row per connected GitHub repository.
     *
     * @param projectId When provided, only repositories connected to that project are returned;
     * otherwise all connected repositories are returned.
     * @return Per-source-instance status rows, ordered by owner and name for stable rendering.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving ingestion status per source instance")
    fun getStatusPerSourceInstance(projectId: UUID? = null): List<SourceInstanceIngestionStatusResponse> =
        githubRepositoryApi.getSourceInstances(projectId).map { it.toStatusResponse() }

    private fun GithubSourceInstanceDto.toStatusResponse(): SourceInstanceIngestionStatusResponse {
        val component = "$owner/$name"
        val lastRun = ingestionRunRepository.findFirstBySourceInstanceIdOrderByStartedAtDesc(repositoryId)
        return SourceInstanceIngestionStatusResponse(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = component,
            repositoryId = repositoryId,
            owner = owner,
            name = name,
            sourceUrl = "https://github.com/$component",
            connectionStatus = status,
            enabled = enabled,
            lastRunTime = lastRun?.startedAt,
            ingestedCount = lastRun?.ingestedCount ?: 0,
            updatedCount = lastRun?.updatedCount ?: 0,
            deletedCount = lastRun?.deletedCount ?: 0,
            failedCount = lastRun?.failedCount ?: 0,
            failedItems = lastRun?.failedItems ?: emptyList(),
            artifactCount = artifactRepository.countByComponent(component),
            lastCommitsSyncAt = lastCommitsSyncAt,
            lastIssuesSyncAt = lastIssuesSyncAt,
            lastPullRequestsSyncAt = lastPullRequestsSyncAt,
        )
    }
}
