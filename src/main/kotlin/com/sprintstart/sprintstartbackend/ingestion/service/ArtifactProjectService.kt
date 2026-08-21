package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.AI_SYNC_STATUS_FAILED
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactProjectsAiSyncResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
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
 * Propagates a source's project links onto its stored artifacts and the AI index.
 *
 * Project membership is held in three places: the connector's connection row, the artifacts'
 * `artifact_projects` mapping, and a marker on every indexed chunk. Retrieval is fail-closed on the
 * last of those, so a link that only reaches the connection leaves the source listed in the new
 * project with none of its content findable there, and an unlink leaves it findable in a project it
 * was just removed from.
 *
 * Source-agnostic on purpose ([ArtifactSourceRef]): every connector needs the same behaviour, and
 * the reuse story (#257) only works if new connectors get it by convention rather than by
 * remembering to reimplement it.
 */
@Service
class ArtifactProjectService(
    private val artifactRepository: ArtifactRepository,
    private val artifactIngestionClient: ArtifactIngestionClient,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // The AI call must not run inside the transaction that changes the mapping, and `@Transactional`
    // does not apply to suspend functions, so the database work is wrapped explicitly -- the same
    // way RunArtifactsIngestionService does it.
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Adds or removes one project across every artifact of a source, then mirrors the result to the
     * AI index.
     *
     * Idempotent in both directions, so a caller that is unsure whether an earlier attempt reached
     * the AI service can simply repeat the operation. The database is written first and is the
     * source of truth: the payload sent onwards is the membership as stored, not a delta, so a
     * repeat converges instead of compounding.
     *
     * @param source The connected source whose artifacts should follow the link change.
     * @param projectId The project that was linked or unlinked.
     * @param linked `true` to add the project, `false` to remove it.
     * @throws IngestionResponseException when the AI service rejects the batch or reports that part
     * of it did not apply.
     */
    @Tracked("Propagating a source's project link to its artifacts")
    suspend fun applyProjectLink(source: ArtifactSourceRef, projectId: UUID, linked: Boolean) {
        val memberships = withContext(Dispatchers.IO) {
            transactionTemplate.execute {
                findArtifactsOf(source)
                    .onEach { artifact ->
                        if (linked) artifact.addProjectId(projectId) else artifact.removeProjectId(projectId)
                    }.map { artifact ->
                        ArtifactProjectsAiRequest(
                            artifactId = artifact.id.toString(),
                            projectIds = artifact.projectIds.map(UUID::toString),
                        )
                    }
            }
        }.orEmpty()

        if (memberships.isEmpty()) {
            logger.info("Source {} has no stored artifacts to re-scope, skipping AI sync", source)
            return
        }

        logger.info(
            "{} project {} on {} artifact(s) of {}",
            if (linked) "Linking" else "Unlinking",
            projectId,
            memberships.size,
            source,
        )

        val response = artifactIngestionClient.syncProjectMemberships(
            ArtifactProjectsAiSyncRequest(memberships),
        )
        requireEveryArtifactSucceeded(source, projectId, response)
    }

    /**
     * Loads every stored artifact of a source, however that source encodes its identity.
     *
     * @param source The connected source to resolve.
     * @return The source's stored artifacts, empty when it has never been ingested.
     */
    private fun findArtifactsOf(source: ArtifactSourceRef): List<Artifact> = when (source) {
        is ArtifactSourceRef.GithubRepository -> artifactRepository.findAllByComponent(source.component)
        is ArtifactSourceRef.JiraInstance -> artifactRepository.findAllJiraArtifactsByInstanceUrl(source.instanceUrl)
    }

    /**
     * Rejects a membership batch the AI service accepted but did not fully apply.
     *
     * @param source The source whose artifacts were being re-scoped.
     * @param projectId The project that was linked or unlinked.
     * @param response The AI service's per-artifact result.
     * @throws IngestionResponseException when any artifact failed.
     */
    private fun requireEveryArtifactSucceeded(
        source: ArtifactSourceRef,
        projectId: UUID,
        response: ArtifactProjectsAiSyncResponse,
    ) {
        val failed = response.artifacts.filter { it.status == AI_SYNC_STATUS_FAILED }
        if (failed.isEmpty()) return

        throw IngestionResponseException(
            "Project $projectId was stored for $source but ${failed.size} artifact(s) " +
                "kept their old membership in the AI index" +
                (failed.firstNotNullOfOrNull { it.errorMessage }?.let { " (first reported cause: $it)" } ?: ""),
        )
    }
}
