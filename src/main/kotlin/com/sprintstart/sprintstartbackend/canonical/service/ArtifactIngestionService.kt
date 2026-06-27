package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.model.mapper.SourceIdFactory.buildSourceId
import com.sprintstart.sprintstartbackend.canonical.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileDeletedEvent
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Owns writes to the canonical artifact store and the mutable parts of `IngestionRun`.
 *
 * Connector listeners do not persist artifacts directly. They first map source-specific events to
 * canonical commands, then delegate here so version-independent business rules stay in one place:
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
     * Persists or updates a canonical artifact for the active ingestion run.
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
     * @throws IllegalArgumentException when the referenced ingestion run does not exist
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
     * connection setup, active fetching, and immediate startup failures.
     */
    @Transactional
    fun startRun(transactionId: UUID, sourceSystem: SourceSystem, status: IngestionRunStatus) {
        val ingestionRun = IngestionRun(
            id = transactionId,
            sourceSystem = sourceSystem,
            status = status,
        )
        ingestionRunRepository.save(ingestionRun)
    }

    /**
     * Updates the run status inside a transaction so listener-triggered state transitions are
     * persisted even when they only mutate the managed entity.
     *
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
     * Removes a canonical file artifact when GitHub reports that the source file was deleted.
     *
     * This does not affect historic run counters; it only removes the current file artifact row
     * addressed by the canonical GitHub `sourceId`.
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
