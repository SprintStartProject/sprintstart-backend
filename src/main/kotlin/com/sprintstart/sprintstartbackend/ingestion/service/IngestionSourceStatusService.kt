package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraSourceInstanceDto
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
 * this service returns one row per connected source instance across every connector (one GitHub
 * repository, one Jira instance, ...): it takes the instance's current connection status and sync
 * timestamps (via each connector's module API) and attaches the counters of that instance's latest
 * ingestion run.
 */
@Service
class IngestionSourceStatusService(
    private val githubRepositoryApi: GithubRepositoryApi,
    private val jiraInstanceApi: JiraInstanceApi,
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Returns one status row per connected source instance across all connectors.
     *
     * @param projectId When provided, only instances connected to that project are returned;
     * otherwise all connected instances are returned.
     * @return Per-source-instance status rows, GitHub instances first then Jira, each connector's
     * rows ordered stably by its module API.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving ingestion status per source instance")
    fun getStatusPerSourceInstance(projectId: UUID? = null): List<SourceInstanceIngestionStatusResponse> {
        val githubStatuses = githubRepositoryApi.getSourceInstances(projectId).map { it.toStatusResponse() }
        val jiraStatuses = jiraInstanceApi.getSourceInstances(projectId).map { it.toStatusResponse() }
        val uploadStatuses = if (projectId != null) {
            val uploadCount = artifactRepository.countUploadArtifactsByProjectId(projectId)
            if (uploadCount > 0) {
                val lastRun = ingestionRunRepository.findFirstBySourceInstanceIdOrderByStartedAtDesc(projectId)
                listOf(
                    SourceInstanceIngestionStatusResponse(
                        sourceSystem = SourceSystem.UPLOAD,
                        sourceId = "Uploads",
                        displayName = "Uploaded files",
                        repositoryId = projectId,
                        owner = null,
                        name = null,
                        sourceUrl = "",
                        connectionStatus = "CONNECTED",
                        enabled = true,
                        lastRunTime = lastRun?.startedAt,
                        ingestedCount = lastRun?.ingestedCount ?: 0,
                        updatedCount = lastRun?.updatedCount ?: 0,
                        deletedCount = lastRun?.deletedCount ?: 0,
                        failedCount = lastRun?.failedCount ?: 0,
                        failedItems = lastRun?.failedItems.orEmpty(),
                        artifactCount = uploadCount,
                        lastCommitsSyncAt = null,
                        lastIssuesSyncAt = null,
                        lastPullRequestsSyncAt = null,
                    ),
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        return githubStatuses + jiraStatuses + uploadStatuses
    }

    private fun GithubSourceInstanceDto.toStatusResponse(): SourceInstanceIngestionStatusResponse {
        val component = "$owner/$name"
        val lastRun = ingestionRunRepository.findFirstBySourceInstanceIdOrderByStartedAtDesc(repositoryId)
        return SourceInstanceIngestionStatusResponse(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = component,
            displayName = component,
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
            failedItems = lastRun?.failedItems.orEmpty(),
            artifactCount = artifactRepository.countByComponent(component),
            lastCommitsSyncAt = lastCommitsSyncAt,
            lastIssuesSyncAt = lastIssuesSyncAt,
            lastPullRequestsSyncAt = lastPullRequestsSyncAt,
        )
    }

    private fun JiraSourceInstanceDto.toStatusResponse(): SourceInstanceIngestionStatusResponse {
        val lastRun = ingestionRunRepository.findFirstBySourceInstanceRefOrderByStartedAtDesc(instanceUrl)
        return SourceInstanceIngestionStatusResponse(
            sourceSystem = SourceSystem.JIRA,
            sourceId = instanceUrl,
            displayName = displayName,
            repositoryId = null,
            owner = null,
            name = null,
            sourceUrl = instanceUrl,
            connectionStatus = status,
            enabled = enabled,
            lastRunTime = lastRun?.startedAt,
            ingestedCount = lastRun?.ingestedCount ?: 0,
            updatedCount = lastRun?.updatedCount ?: 0,
            deletedCount = lastRun?.deletedCount ?: 0,
            failedCount = lastRun?.failedCount ?: 0,
            failedItems = lastRun?.failedItems.orEmpty(),
            artifactCount = artifactRepository.countJiraArtifactsByInstanceUrl(instanceUrl),
            lastCommitsSyncAt = null,
            lastIssuesSyncAt = lastUpdate,
            lastPullRequestsSyncAt = null,
        )
    }
}
