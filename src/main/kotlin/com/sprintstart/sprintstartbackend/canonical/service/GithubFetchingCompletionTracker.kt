package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Tracks completion of the distinct GitHub fetch phases that contribute to one ingestion run.
 *
 * A GitHub run is considered complete only after every expected phase reports completion or
 * terminal failure. This service derives the final `IngestionRunStatus` from both phase coverage
 * and run counters, which keeps listener code simple and centralizes the "completed vs partial vs
 * failed" decision in one place.
 */
@Service
class GithubFetchingCompletionTracker(
    private val artifactIngestionService: ArtifactIngestionService,
    private val ingestionRunRepository: IngestionRunRepository,
) {
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
     * @throws NoSuchElementException when the run id does not exist
     */
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
