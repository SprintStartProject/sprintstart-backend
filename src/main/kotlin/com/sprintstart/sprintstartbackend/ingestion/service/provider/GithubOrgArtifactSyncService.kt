package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.AI_SYNC_STATUS_FAILED
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactProjectsAiSyncResponse
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Synchronizes project memberships of a GitHub organization metadata artifact with the AI index.
 *
 * Organization metadata artifacts encompass all repositories under the same owner. When an individual
 * repository is linked to or unlinked from a project, the organization artifact must reflect the union
 * of all projects across all remaining connected repositories of that owner. This service handles
 * updating the artifact's database project associations and mirroring the change to the AI index.
 */
@Service
class GithubOrgArtifactSyncService(
    private val artifactRepository: ArtifactRepository,
    private val githubRepositoryApi: GithubRepositoryApi,
    private val artifactIngestionClient: ArtifactIngestionClient,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Reconciles project memberships of an organization metadata artifact with all currently connected
     * repositories owned by that organization, then mirrors the result to the AI index.
     *
     * @param owner The repository owner or organization login.
     * @throws IngestionResponseException when the AI service rejects the batch or reports that part of it
     * did not apply.
     */
    @Tracked("Reconciling GitHub org artifact project memberships")
    suspend fun syncOrgArtifact(owner: String) {
        val (artifactId, targetProjectIds) = withContext(Dispatchers.IO) {
            transactionTemplate.execute {
                val artifact = artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, owner)
                    ?: return@execute null
                val targetProjectIds = githubRepositoryApi.getProjectIdsByOwner(owner)
                if (artifact.setProjectIds(targetProjectIds)) {
                    artifactRepository.save(artifact)
                    artifact.id to targetProjectIds
                } else {
                    null
                }
            }
        } ?: return

        logger.info(
            "Syncing org artifact {} for owner {} with {} project(s) to AI index",
            artifactId,
            owner,
            targetProjectIds.size,
        )

        val response = artifactIngestionClient.syncProjectMemberships(
            ArtifactProjectsAiSyncRequest(
                listOf(
                    ArtifactProjectsAiRequest(
                        artifactId = artifactId.toString(),
                        projectIds = targetProjectIds.map(UUID::toString),
                    ),
                ),
            ),
        )
        requireOrgArtifactSucceeded(owner, response)
    }

    private fun requireOrgArtifactSucceeded(
        owner: String,
        response: ArtifactProjectsAiSyncResponse,
    ) {
        val failed = response.artifacts.filter { it.status == AI_SYNC_STATUS_FAILED }
        if (failed.isEmpty()) return

        throw IngestionResponseException(
            "Org artifact for $owner was updated locally but kept its old membership in the AI index" +
                (failed.firstNotNullOfOrNull { it.errorMessage }?.let { " (first reported cause: $it)" } ?: ""),
        )
    }
}
