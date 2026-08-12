package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.UploadArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Owns upload-backed writes to the ingestion artifact store and related run counters.
 *
 * Upload listeners do not persist artifacts directly. They first map upload events to ingestion
 * commands, then delegate here so duplicate detection, content-change updates, deletion tracking,
 * and run counters stay in one transactional boundary.
 */
@Service
class UploadArtifactProviderService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Persists or updates an upload-backed ingestion artifact for the active run.
     *
     * Business rules:
     * - upload artifacts are idempotent by `sourceId`
     * - an existing upload artifact is updated only when the incoming content hash changes
     * - the current project id is added to the artifact before change detection returns
     *
     * Counter side effects happen inside the same transaction:
     * - `ingestedCount` increments only when a new artifact row is created
     * - `updatedCount` increments only when an existing artifact is changed
     *
     * @param command The mapped upload artifact command containing source identity, project id,
     * content, and upload metadata.
     */
    @Transactional
    fun persistArtifact(command: UploadArtifactCommand) {
        val runId = command.ingestionRunId
        val projectId = command.projectId

        var artifact = artifactRepository.findBySourceId(command.sourceId)
        if (artifact != null) {
            artifact.addProjectId(projectId)
            if (artifact.hash != command.hash) {
                artifact.content = command.content
                artifact.hash = command.hash
                artifact.markAiSyncPending(runId)
                val ingestionRun = ingestionRunRepository.findByIdForUpdate(command.ingestionRunId).orElseThrow {
                    IngestionRunNotFoundException(command.ingestionRunId)
                }
                ingestionRun.updatedCount++
            }
            return
        }
        val ingestionRun = ingestionRunRepository.findByIdForUpdate(command.ingestionRunId).orElseThrow {
            IngestionRunNotFoundException(command.ingestionRunId)
        }
        artifact = Artifact(
            sourceSystem = command.sourceSystem,
            sourceId = command.sourceId,
            sourceUrl = null,
            artifactType = command.artifactType,
            title = command.title,
            content = command.content,
            mime = command.mime,
            language = command.language,
            projectIdsInternal = mutableSetOf(projectId),
            ingestionRun = ingestionRun,
            hash = command.hash,
            createdAtSource = null,
            updatedAtSource = null,
            aiSyncRunId = runId,
        )
        artifactRepository.save(artifact)
        ingestionRun.ingestedCount++
    }

    /**
     * Removes an ingestion artifact when the matching uploaded artifact is deleted.
     *
     * The upload artifact id is used as the ingestion source id. When a matching artifact exists,
     * its ingestion artifact id is recorded for AI deindexing and `deletedCount` is incremented on
     * the locked run. If no matching ingestion artifact exists, the run is left unchanged.
     *
     * @param transactionId The ingestion run id for the upload deletion batch.
     * @param uploadArtifactId The uploaded artifact id used as the ingestion artifact source id.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun deleteArtifact(transactionId: UUID, uploadArtifactId: UUID) {
        val run = ingestionRunRepository.findByIdForUpdate(transactionId).orElseThrow {
            IngestionRunNotFoundException(transactionId)
        }
        val sourceId = uploadArtifactId.toString()
        val artifact = artifactRepository.findBySourceId(sourceId) ?: return

        artifactRepository.deleteById(artifact.id)
        run.deletedCount++
        run.artifactIdsToDeindex.add(artifact.id.toString())
    }
}
