package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.JiraArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * Owns writes to the ingestion artifact store for Jira issue artifacts and the mutable parts
 * of `IngestionRun`.
 *
 * Connector listeners do not persist artifacts directly. They first map source-specific events to
 * ingestion commands, then delegate here so version-independent business rules stay in one place:
 * - New issues create a fresh artifact row (`ingestedCount++`).
 * - Re-fetched issues update the existing row in place, tracking churn via `updatedCount++`.
 * - Deleted issues remove the artifact and record it for AI deindexing.
 */
@Service
class JiraArtifactProviderService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val artifactMetadataJsonMapper: ArtifactMetadataJsonMapper,
) {
    /**
     * Persists or updates an ingestion artifact for the active ingestion run.
     *
     * If the issue already exists in the artifact store (by `issueId`), the existing artifact
     * is updated in place and `updatedCount` is incremented. Otherwise a new artifact is created
     * and `ingestedCount` is incremented.
     *
     * @param command The mapped Jira artifact command containing issue content and metadata.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun persistArtifact(command: JiraArtifactCommand) {
        val runId = command.ingestionRunId

        val existing = artifactRepository.findBySourceId(command.issueId)
        if (existing != null) {
            existing.title = command.summary
            existing.content = command.description
            existing.addProjectIds(command.projectIds)
            existing.metadata = artifactMetadataJsonMapper.toJson(command.toMetadata())
            val ingestionRun = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
                IngestionRunNotFoundException(runId)
            }
            ingestionRun.updatedCount++
            return
        }

        val ingestionRun = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
        }
        val artifact = Artifact(
            sourceSystem = command.sourceSystem,
            sourceId = command.issueId,
            sourceUrl = command.sourceUrl,
            artifactType = command.artifactType,
            title = command.summary,
            content = command.description,
            mime = null,
            language = null,
            projectIdsInternal = command.projectIds.toMutableSet(),
            ingestionRun = ingestionRun,
            createdAtSource = command.createdAt,
            updatedAtSource = command.updatedAt,
            hash = null,
            metadata = artifactMetadataJsonMapper.toJson(command.toMetadata()),
        )
        artifactRepository.save(artifact)
        ingestionRun.ingestedCount++
    }

    /**
     * Removes an ingestion artifact when Jira reports that the source issue was deleted and
     * records its id for AI deindexing at the end of the run.
     *
     * The run is locked because deletion events mutate both `deletedCount` and the deindex list. If
     * no stored artifact exists for the deleted issue id, the method leaves the run unchanged.
     *
     * @param event The Jira issue deletion event containing the issue id.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun deleteFileArtifact(event: JiraIssueDeletedEvent) {
        val run = ingestionRunRepository.findByIdForUpdate(event.transactionId).orElseThrow {
            IngestionRunNotFoundException(event.transactionId)
        }

        val artifact = artifactRepository.findBySourceId(event.issueId) ?: return

        artifactRepository.deleteById(artifact.id)
        run.deletedCount++
        run.artifactIdsToDeindex.add(artifact.id.toString())
    }
}
