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
 * Tracks Bitbucket fetch-phase completion before finalizing the ingestion run.
 *
 * Mirrors [GithubIngestionRunService] with one deliberate difference: the phase set a Bitbucket run
 * waits for is [PHASES], not every [FinishedTypes] entry. Bitbucket has no issue tracker and no
 * org-metadata collection, so a run that waited for `ISSUES` or `ORG_METADATA` the way the GitHub
 * one does would never finish.
 *
 * The run is finalized only after every expected phase has reported completion, so a successful
 * fast phase cannot publish the run-finished event while slower phases are still writing artifacts.
 *
 * @constructor Creates the service from its repositories.
 * @param ingestionRunRepository Loads and locks the run whose phases are tracked.
 * @param ingestionRunLifeCycleService Applies the shared terminal-status rule and emits the
 *        run-finished event.
 */
@Service
class BitbucketIngestionRunService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    /**
     * Uses a locked run because the three Bitbucket phase-completion events can arrive concurrently
     * and each event mutates the same `finishedTypes` set before checking whether the run is done.
     *
     * The run is finalized only once the set contains every [PHASES] entry.
     *
     * @param runId The ingestion run whose completed fetch phase should be recorded.
     * @param finishedType The Bitbucket fetch phase that has just completed.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    @Tracked("Marking fetch phase as finished")
    fun markFetchPhaseFinished(runId: UUID, finishedType: FinishedTypes) {
        val run = ingestionRunRepository
            .findByIdForUpdate(runId)
            .orElseThrow { IngestionRunNotFoundException(runId) }
        // Same guard as the failure path: a phase reported twice must not finish the run twice.
        if (!run.finishedTypes.add(finishedType)) {
            return
        }
        if (run.finishedTypes.containsAll(PHASES)) {
            ingestionRunLifeCycleService.finishRun(run)
        }
    }

    /**
     * Records that a whole fetch phase failed, then closes the phase.
     *
     * A phase that fails outright — an unreachable remote, a clone that will not start, a response
     * that does not deserialize — must not look like a phase that legitimately found nothing: the
     * failure is recorded so the shared terminal-status rule can turn the run into `PARTIAL` or
     * `FAILED` instead of `COMPLETED` with no artifacts.
     *
     * @param runId The ingestion run whose fetch phase failed.
     * @param finishedType The Bitbucket fetch phase that failed.
     * @param reason What the connector reported, surfaced with the run's failed items.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    @Tracked("Marking fetch phase as failed")
    fun markFetchPhaseFailed(runId: UUID, finishedType: FinishedTypes, reason: String) {
        val run = ingestionRunRepository
            .findByIdForUpdate(runId)
            .orElseThrow { IngestionRunNotFoundException(runId) }

        // Gated on the phase not having closed yet: the same failure can be reported twice, and an
        // ungated second report would finish the run twice, firing the AI sync a second time.
        if (!run.finishedTypes.add(finishedType)) {
            return
        }

        run.failedItems.add(
            FailedArtifact(
                sourceId = null,
                artifactType = finishedType.toArtifactType(),
                sourceUrl = null,
                reason = "Fetching ${finishedType.name.lowercase()} failed: $reason",
            ),
        )
        run.failedCount++

        if (run.finishedTypes.containsAll(PHASES)) {
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

    private companion object {
        /** The fetch phases a Bitbucket run consists of. */
        val PHASES: Set<FinishedTypes> = setOf(
            FinishedTypes.COMMITS,
            FinishedTypes.FILES,
            FinishedTypes.PULL_REQUESTS,
        )
    }
}
