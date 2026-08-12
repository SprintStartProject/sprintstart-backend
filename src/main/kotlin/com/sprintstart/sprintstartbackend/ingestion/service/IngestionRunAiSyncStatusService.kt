package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.events.RunIndexedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactAiSyncState
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Derives a run's `aiSyncStatus` from the AI-sync state of the artifacts it touched.
 *
 * With per-artifact syncing there is no single moment at which "the run's sync" succeeds or fails,
 * so the run-level status stops being written directly and becomes a roll-up instead. It is
 * recomputed after every drained batch, which keeps the existing API contract
 * (`IngestionRunResponse.aiSyncStatus`, the run history UI) working unchanged while the mechanism
 * underneath it changed completely.
 *
 * Attribution follows `Artifact.aiSyncRunId` -- the run that last marked the artifact pending --
 * not `ingestionRun`, so a run that only *updated* existing artifacts still reports on them.
 */
@Service
class IngestionRunAiSyncStatusService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val publisher: ApplicationEventPublisher,
) {
    /**
     * Recomputes and stores one run's AI sync status.
     *
     * Precedence is "still owed" before "gave up": while anything is pending the run is [PENDING],
     * because a failed artifact may still be retried out of that pending set. A run that touched no
     * artifacts at all is left alone -- it may legitimately be `NOT_APPLICABLE` (nothing to sync) or
     * a deindex-only run whose status the deindex path owns.
     *
     * @param runId The run to roll up; unknown ids are ignored.
     */
    @Transactional
    fun recompute(runId: UUID) {
        val run = ingestionRunRepository.findByIdOrNull(runId) ?: return

        val pending = artifactRepository.countByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.PENDING)
        val failed = artifactRepository.countByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.FAILED)
        val synced = artifactRepository.countByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.SYNCED)

        if (pending + failed + synced == 0L) {
            return
        }

        when {
            pending > 0L -> {
                run.aiSyncStatus = AiSyncStatus.PENDING
                run.aiSyncFailureReason = null
            }

            failed > 0L -> {
                run.aiSyncStatus = AiSyncStatus.FAILED
                run.aiSyncFailureReason = failureReason(runId, failed)
            }

            else -> {
                val wasAlreadyIndexed = run.aiSyncStatus == AiSyncStatus.SUCCEEDED
                run.aiSyncStatus = AiSyncStatus.SUCCEEDED
                run.aiSyncFailureReason = null

                // The corpus now contains this run, which is the first moment anything can be
                // derived from it. Published on the *transition* only: this method runs after
                // every drained batch, and firing each time would start a generation run per
                // batch instead of one per crawl.
                if (!wasAlreadyIndexed) {
                    publisher.publishEvent(
                        RunIndexedEvent(
                            runId = runId,
                            projectIds = artifactRepository
                                .findDistinctProjectIdsByAiSyncRunId(runId)
                                .toSet(),
                        ),
                    )
                }
            }
        }
    }

    private fun failureReason(runId: UUID, failed: Long): String {
        val firstError = artifactRepository
            .findAllByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.FAILED)
            .firstNotNullOfOrNull { it.aiSyncError }

        return "$failed artifact(s) could not be indexed" + (firstError?.let { ": $it" } ?: "")
    }
}
