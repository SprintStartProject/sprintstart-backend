package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
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
     * @param transactionId The ingestion run id to update.
     * @param status The status to persist.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
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
        }
    }
}
