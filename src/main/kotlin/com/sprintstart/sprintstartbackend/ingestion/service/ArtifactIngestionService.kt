package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildSourceId
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Owns writes to the ingestion artifact store and the mutable parts of `IngestionRun`.
 *
 * Connector listeners do not persist artifacts directly. They first map source-specific events to
 * ingestion commands, then delegate here so version-independent business rules stay in one place:
 * duplicate commits are ignored, files and issues update existing rows only when their effective
 * content changes, pull requests are treated as mutable records, and run counters are updated in
 * the same transaction as the underlying entity changes.
 */
@Service
class ArtifactIngestionService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Persists or updates an ingestion artifact for the active ingestion run.
     *
     * Business rules:
     * - commits are idempotent by `sourceId`; an already-known commit is ignored
     * - files are updated only when the incoming content hash changes
     * - issues are updated only when the computed issue hash changes
     * - pull requests are always treated as mutable and overwrite title/body on re-fetch
     *
     * Counter side effects happen inside the same transaction:
     * - `ingestedCount` increments only when a new artifact row is created
     * - `updatedCount` increments only when an existing artifact is changed
     *
     * @param command [ArtifactCommand] the command containing all data needed for ingestion.
     * @throws IllegalArgumentException when the referenced ingestion run does not exist.
     */
    @Transactional
    fun ingest(command: ArtifactCommand) {
        var artifact: Artifact?
        val ingestionRun = ingestionRunRepository
            .findByIdOrNull(command.ingestionRunId)
            ?: throw IllegalArgumentException("Run with id ${command.ingestionRunId} not found")
        when (command.artifactType) {
            ArtifactType.COMMIT,
            -> {
                artifact = artifactRepository.findBySourceId(command.sourceId)
                if (artifact != null) {
                    return
                }
            }

            ArtifactType.FILE,
            -> {
                artifact = artifactRepository.findBySourceId(command.sourceId)
                if (artifact != null) {
                    if (artifact.hash != command.hash) {
                        artifact.bodyText = command.bodyText
                        artifact.hash = command.hash
                        ingestionRun.updatedCount++
                    }
                    return
                }
            }

            ArtifactType.ISSUE,
            -> {
                artifact = artifactRepository.findBySourceId(command.sourceId)
                if (artifact != null) {
                    if (artifact.hash != command.hash) {
                        artifact.title = command.title
                        artifact.bodyText = command.bodyText
                        ingestionRun.updatedCount++
                    }
                    return
                }
            }

            ArtifactType.PULL_REQUEST,
            -> {
                artifact = artifactRepository.findBySourceId(command.sourceId)
                if (artifact != null) {
                    artifact.title = command.title
                    artifact.bodyText = command.bodyText
                    ingestionRun.updatedCount++
                    return
                }
            }
        }

        artifact = Artifact(
            sourceSystem = command.sourceSystem,
            sourceId = command.sourceId,
            sourceUrl = command.sourceUrl,
            artifactType = command.artifactType,
            title = command.title,
            bodyText = command.bodyText,
            mime = command.mime,
            language = command.language,
            ingestionRun = ingestionRun,
            hash = command.hash,
            createdAtSource = null,
            updatedAtSource = null,
        )
        artifactRepository.save(artifact)

        ingestionRun.ingestedCount++
    }

    /**
     * Creates an ingestion run before connector work begins.
     *
     * The initial status is controlled by the caller so listeners can distinguish between
     * connection setup, active fetching, and immediate startup failures. If the run already
     * exists, the mutable lifecycle fields are updated instead of creating a duplicate row.
     *
     * @param transactionId The id of the transaction to start.
     * @param sourceSystem [SourceSystem] The source of the new run.
     * @param status [IngestionRunStatus] The status of the ingestion run.
     * @param failureReason Optional run-level failure reason for lifecycle failures.
     */
    @Transactional
    fun startRun(
        transactionId: UUID,
        sourceSystem: SourceSystem,
        status: IngestionRunStatus,
        failureReason: String? = null,
    ) {
        val ingestionRun = ingestionRunRepository.findByIdOrNull(transactionId)
        if (ingestionRun == null) {
            val ingestionRun = IngestionRun(
                id = transactionId,
                sourceSystem = sourceSystem,
                status = status,
                failureReason = failureReason,
                finishedAt = if (status == IngestionRunStatus.FAILED) Instant.now() else null,
            )
            ingestionRunRepository.save(ingestionRun)
        } else {
            ingestionRun.status = status
            ingestionRun.finishedAt = if (status == IngestionRunStatus.FAILED) Instant.now() else null
            ingestionRun.failureReason = failureReason
        }
    }

    /**
     * Updates the run status inside a transaction so listener-triggered state transitions are
     * persisted even when they only mutate the managed entity.
     *
     * @param transactionId The id of the transaction to update the status of.
     * @param status [IngestionRunStatus] The new status.
     * @throws IllegalArgumentException when the run id is unknown
     */
    @Transactional
    fun updateRunStatus(transactionId: UUID, status: IngestionRunStatus) {
        val run = ingestionRunRepository
            .findByIdOrNull(transactionId)
            ?: throw IllegalArgumentException("Run with id $transactionId not found")
        run.status = status
    }

    /**
     * Appends one failed source artifact to the current run and increments the aggregated failure
     * counter in the same transaction.
     *
     * The individual failed item is preserved for status/history views that need artifact-level
     * error details without scanning connector logs.
     *
     * @param command [ArtifactFailedCommand] The command for a failed artifact containing all data needed.
     * @throws IllegalArgumentException when the run id is unknown
     */
    @Transactional
    fun addFailedArtifact(command: ArtifactFailedCommand) {
        val run = ingestionRunRepository
            .findByIdOrNull(command.transactionId)
            ?: throw IllegalArgumentException("Run with id ${command.transactionId} not found")
        run.failedItems.add(
            FailedArtifact(
                sourceId = command.sourceId,
                reason = command.reason,
                artifactType = command.artifactType,
                sourceUrl = command.sourceUrl,
            ),
        )
        run.failedCount++
    }

    /**
     * Removes an ingestion file artifact when GitHub reports that the source file was deleted.
     *
     * This does not affect historic run counters; it only removes the current file artifact row
     * addressed by the ingestion GitHub `sourceId`.
     *
     * @param event [GithubFileDeletedEvent] The event, emitted by the GitHub module, indicating a file deletion.
     */
    @Transactional
    fun unIngestFileArtifact(event: GithubFileDeletedEvent) {
        artifactRepository.deleteBySourceId(
            buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.FILE,
                unique = event.path,
            ),
        )
    }
}
