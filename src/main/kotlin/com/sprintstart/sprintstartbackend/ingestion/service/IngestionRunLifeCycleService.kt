package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Centralizes ingestion run lifecycle transitions shared by source-specific listeners.
 *
 * Source modules are responsible for deciding when their work starts and when all source-specific
 * artifacts have been processed. This service applies the common status rules and emits the
 * run-finished event only for runs that produced at least a partial result.
 */
@Service
class IngestionRunLifeCycleService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val publisher: ApplicationEventPublisher,
) {
    /**
     * Creates an ingestion run before connector work begins.
     *
     * The initial status is controlled by the caller so listeners can distinguish between
     * connection setup, active fetching, and immediate startup failures. If the run already
     * exists, the mutable lifecycle fields are updated instead of creating a duplicate row.
     *
     * @param transactionId The source transaction id that also identifies the ingestion run.
     * @param sourceSystem The external system that owns the run.
     * @param status The initial or replacement lifecycle status.
     * @param failureReason Optional run-level failure reason for startup failures.
     * @param sourceInstanceId Optional connector-neutral id of the source instance this run belongs
     * to (for GitHub the connected repository id).
     * @param sourceInstanceRef Optional denormalized label of the source instance (for GitHub
     * "owner/name").
     */
    @Transactional
    @Tracked("Starting ingestion run")
    fun startOrUpdateRun(
        transactionId: UUID,
        sourceSystem: SourceSystem,
        status: IngestionRunStatus,
        failureReason: String? = null,
        sourceInstanceId: UUID? = null,
        sourceInstanceRef: String? = null,
    ) {
        val ingestionRun = ingestionRunRepository.findByIdOrNull(transactionId)
        if (ingestionRun == null) {
            val initialAiSyncStatus =
                if (status == IngestionRunStatus.FAILED) AiSyncStatus.NOT_APPLICABLE else AiSyncStatus.PENDING
            val ingestionRun = IngestionRun(
                id = transactionId,
                sourceSystem = sourceSystem,
                sourceInstanceId = sourceInstanceId,
                sourceInstanceRef = sourceInstanceRef,
                status = status,
                failureReason = failureReason.truncateToDbLimit(),
                finishedAt = if (status == IngestionRunStatus.FAILED) Instant.now() else null,
                aiSyncStatus = initialAiSyncStatus,
            )
            ingestionRunRepository.save(ingestionRun)
        } else {
            ingestionRun.status = status
            ingestionRun.finishedAt = if (status == IngestionRunStatus.FAILED) Instant.now() else null
            ingestionRun.failureReason = failureReason.truncateToDbLimit()
            if (status == IngestionRunStatus.FAILED) {
                ingestionRun.aiSyncStatus = AiSyncStatus.NOT_APPLICABLE
            }
            // Backfill source-instance metadata if a later lifecycle call resolved it.
            if (ingestionRun.sourceInstanceId == null) ingestionRun.sourceInstanceId = sourceInstanceId
            if (ingestionRun.sourceInstanceRef == null) ingestionRun.sourceInstanceRef = sourceInstanceRef
        }
    }

    /**
     * Updates the run status inside a transaction so listener-triggered state transitions are
     * persisted even when they only mutate the managed entity.
     *
     * @param transactionId The ingestion run id to update.
     * @param status The status to persist.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    @Tracked("Updating ingestion run status")
    fun updateRunStatus(transactionId: UUID, status: IngestionRunStatus) {
        val run = ingestionRunRepository
            .findByIdOrNull(transactionId)
            ?: throw IngestionRunNotFoundException(transactionId)
        run.status = status
    }

    /**
     * Applies the shared terminal-status rule for all source systems.
     *
     * A run with failures is `PARTIAL` when at least one artifact was ingested, updated, or deleted;
     * otherwise it is `FAILED`. Fully failed runs do not publish `RunFinishedEvent`, because there
     * is nothing for the AI sync layer to ingest or deindex.
     *
     * @param run The managed ingestion run entity whose terminal status should be calculated.
     */
    @Transactional
    @Tracked("Finishing ingestion run")
    fun finishRun(run: IngestionRun) {
        if (run.failedCount > 0) {
            if (run.ingestedCount > 0 || run.updatedCount > 0 || run.deletedCount > 0) {
                run.status = IngestionRunStatus.PARTIAL
            } else {
                run.status = IngestionRunStatus.FAILED
            }
        } else {
            run.status = IngestionRunStatus.COMPLETED
        }

        run.finishedAt = Instant.now()
        if (run.status in setOf(IngestionRunStatus.COMPLETED, IngestionRunStatus.PARTIAL)) {
            publisher.publishEvent(RunFinishedEvent(run.id))
        } else {
            // Nothing was ingested, updated, or deleted, so there is nothing for the AI
            // sync layer to act on -- it will never run for this id.
            run.aiSyncStatus = AiSyncStatus.NOT_APPLICABLE
        }
    }

    /**
     * Records that a run's artifacts were successfully synced to the AI service.
     *
     * A no-op sync (nothing new to send) also counts as success -- the AI service's index
     * already correctly reflects this run either way.
     *
     * @param runId The ingestion run whose AI sync just completed.
     */
    @Transactional
    @Tracked("Marking AI sync succeeded")
    fun markAiSyncSucceeded(runId: UUID) {
        ingestionRunRepository.findByIdOrNull(runId)?.let { run ->
            run.aiSyncStatus = AiSyncStatus.SUCCEEDED
        }
    }

    /**
     * Records that a run's AI sync attempt failed, so API consumers can tell "saved locally"
     * apart from "actually searchable in chat" instead of the run appearing complete forever.
     *
     * @param runId The ingestion run whose AI sync failed.
     * @param reason A short description of the failure, surfaced alongside the run.
     */
    @Transactional
    @Tracked("Marking AI sync failed")
    fun markAiSyncFailed(runId: UUID, reason: String?) {
        ingestionRunRepository.findByIdOrNull(runId)?.let { run ->
            run.aiSyncStatus = AiSyncStatus.FAILED
            run.aiSyncFailureReason = reason.truncateToDbLimit()
        }
    }
}

private const val FAILURE_REASON_MAX_LENGTH = 255

/**
 * Truncates the string to the maximum length supported by the database column.
 *
 * Failure reasons come from exception messages that can easily exceed the 255 character
 * limit of the varchar column. This keeps the first 255 characters so the reason is still
 * useful without failing the persistence layer.
 */
private fun String?.truncateToDbLimit(): String? {
    if (this == null || this.length <= FAILURE_REASON_MAX_LENGTH) return this
    return this.take(FAILURE_REASON_MAX_LENGTH - 1) + "…"
}
