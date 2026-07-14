package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Tracks GitHub fetch-phase completion before finalizing the ingestion run.
 *
 * GitHub fetch work completes independently for commits, files, issues, and pull requests. The
 * run is finalized only after every phase has reported completion, so a successful fast phase
 * cannot publish the run-finished event while slower phases are still writing artifacts.
 */
@Service
class GithubIngestionRunService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    /**
     * Uses a locked run because several GitHub phase-completion events can arrive concurrently and
     * each event mutates the same `finishedTypes` set before checking whether the run is complete.
     *
     * The run is finalized only once the set contains every [FinishedTypes] entry.
     *
     * @param runId The ingestion run whose completed fetch phase should be recorded.
     * @param finishedType The GitHub fetch phase that has just completed.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun markFetchPhaseFinished(runId: UUID, finishedType: FinishedTypes) {
        val run = ingestionRunRepository
            .findByIdForUpdate(runId)
            .orElseThrow { IngestionRunNotFoundException(runId) }
        run.finishedTypes.add(finishedType)
        if (run.finishedTypes.containsAll(FinishedTypes.entries)) {
            ingestionRunLifeCycleService.finishRun(run)
        }
    }
}
