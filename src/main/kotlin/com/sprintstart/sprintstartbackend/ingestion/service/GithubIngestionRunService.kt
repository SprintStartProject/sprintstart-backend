package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
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
    @Tracked("Marking fetch phase as finished")
    fun markFetchPhaseFinished(runId: UUID, finishedType: FinishedTypes) {
        val run = ingestionRunRepository
            .findByIdForUpdate(runId)
            .orElseThrow { IngestionRunNotFoundException(runId) }
        run.finishedTypes.add(finishedType)
        if (run.finishedTypes.containsAll(FinishedTypes.entries)) {
            ingestionRunLifeCycleService.finishRun(run)
        }
    }

    /**
     * Records that a whole fetch phase failed, then closes the phase.
     *
     * A phase that fails outright -- a rejected query, an unreachable API, a response that does not
     * deserialize -- used to be indistinguishable from one that legitimately found nothing: the
     * phase was simply marked finished and the run ended up `COMPLETED` with no artifacts. Counting
     * it as a failure lets the shared terminal-status rule turn the run into `PARTIAL` or `FAILED`,
     * so the run history stops claiming a sync happened when none did.
     *
     * @param runId The ingestion run whose fetch phase failed.
     * @param finishedType The GitHub fetch phase that failed.
     * @param reason What the connector reported, surfaced with the run's failed items.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    @Tracked("Marking fetch phase as failed")
    fun markFetchPhaseFailed(runId: UUID, finishedType: FinishedTypes, reason: String) {
        val run = ingestionRunRepository
            .findByIdForUpdate(runId)
            .orElseThrow { IngestionRunNotFoundException(runId) }

        run.failedItems.add(
            FailedArtifact(
                sourceId = null,
                artifactType = finishedType.toArtifactType(),
                sourceUrl = null,
                reason = "Fetching ${finishedType.name.lowercase()} failed: $reason",
            ),
        )
        run.failedCount++

        run.finishedTypes.add(finishedType)
        if (run.finishedTypes.containsAll(FinishedTypes.entries)) {
            ingestionRunLifeCycleService.finishRun(run)
        }
    }

    /**
     * The artifact type a fetch phase produces, so a phase-level failure can be recorded with the
     * same shape as the per-artifact ones.
     */
    private fun FinishedTypes.toArtifactType(): ArtifactType = when (this) {
        FinishedTypes.COMMITS -> ArtifactType.COMMIT
        FinishedTypes.FILES -> ArtifactType.FILE
        FinishedTypes.ISSUES -> ArtifactType.ISSUE
        FinishedTypes.PULL_REQUESTS -> ArtifactType.PULL_REQUEST
        FinishedTypes.ORG_METADATA -> ArtifactType.ORG_METADATA
    }
}
