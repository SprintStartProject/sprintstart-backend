package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.SourceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Builds the compact "latest status per source" view used by operational UIs.
 *
 * Unlike run history, this service collapses the persistence model down to the latest known run
 * for each exposed source system and also defines the empty-state behavior when a source has never
 * run.
 */
@Service
class IngestionStatusService(
    private val ingestionRunRepository: IngestionRunRepository,
) {
    /**
     * Returns the latest status row for each source currently exposed by the API.
     *
     * For the current GitHub-only v0 shape, the method returns a single row. When no run exists,
     * counters default to zero and the timestamp stays null so clients can render a "no runs yet"
     * state without special-case error handling.
     *
     * @return One status row per supported source system.
     */
    fun getIngestionStatusPerSource(): List<SourceIngestionStatusResponse> {
        val lastRun = ingestionRunRepository.findFirstByOrderByStartedAtDesc()
        val github = SourceIngestionStatusResponse(
            sourceSystem = SourceSystem.GITHUB,
            lastRunTime = lastRun?.startedAt,
            ingestedCount = lastRun?.ingestedCount ?: 0,
            updatedCount = lastRun?.updatedCount ?: 0,
            failedCount = lastRun?.failedCount ?: 0,
            failedItems = lastRun?.failedItems ?: mutableListOf(),
            status = lastRun?.status,
        )

        return listOf(github) // TODO add jira etc. later
    }

    /**
     * Marks one GitHub fetch phase as finished and finalizes the run when all phases are present.
     *
     * Final status rules:
     * - `COMPLETED` when all phases finished and no failures were recorded
     * - `PARTIAL` when failures exist but at least one artifact was ingested or updated
     * - `FAILED` when all phases finished and only failures were recorded
     *
     * `finishedAt` is updated on every call so the run reflects the latest phase completion time.
     *
     * @param runId The unique identifier of the ingestion run whose phase has finished.
     * @param finishedType The GitHub fetch phase that reached a terminal state.
     * @throws NoSuchElementException when the run id does not exist
     */
    @Transactional
    fun markFetchPhaseFinished(runId: UUID, finishedType: FinishedTypes) {
        val run = ingestionRunRepository
            .findById(runId)
            .orElseThrow { NoSuchElementException("Run with id $runId not found") }
        run.finishedTypes.add(finishedType)
        if (run.finishedTypes.containsAll(FinishedTypes.entries)) {
            if (run.failedCount > 0) {
                if (run.ingestedCount > 0 || run.updatedCount > 0) {
                    run.status = IngestionRunStatus.PARTIAL
                } else {
                    run.status = IngestionRunStatus.FAILED
                }
            } else {
                run.status = IngestionRunStatus.COMPLETED
            }
        }
        run.finishedAt = Instant.now()
    }
}
