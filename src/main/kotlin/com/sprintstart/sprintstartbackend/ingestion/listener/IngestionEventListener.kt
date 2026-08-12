package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunAiSyncStatusService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import com.sprintstart.sprintstartbackend.ingestion.service.RunArtifactsIngestionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class IngestionEventListener(
    private val runArtifactsIngestionService: RunArtifactsIngestionService,
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
    private val ingestionRunAiSyncStatusService: IngestionRunAiSyncStatusService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Finishes a run's AI-side work: flushes its deindex list and rolls up its sync status.
     *
     * Indexing is *not* triggered here any more -- artifacts are synced incrementally while the
     * crawl is still running (see [com.sprintstart.sprintstartbackend.ingestion.service.ArtifactAiSyncService]),
     * so most of a run's content is already searchable by the time this fires. What is left is the
     * part that cannot be tracked per artifact, because the rows are gone: deletions.
     *
     * Runs on a fire-and-forget coroutine, so nothing upstream awaits this. A failed deindex is
     * recorded on the run itself, otherwise it would vanish into [applicationScope]'s
     * uncaught-exception handling with no link back to the run it belonged to. The status roll-up
     * runs otherwise, so a run whose artifacts are still queued keeps reporting `PENDING` rather
     * than being frozen at whatever the previous write left behind.
     *
     * @param event The run-finished event containing the ingestion run id.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRunFinished(
        event: RunFinishedEvent,
    ) {
        applicationScope.launch {
            try {
                runArtifactsIngestionService.deindexRunArtifacts(event.runId)
            } catch (e: Exception) {
                logger.error("AI deindex failed for ingestion run {}", event.runId, e)
                ingestionRunLifeCycleService.markAiSyncFailed(event.runId, e.message)
                return@launch
            }
            ingestionRunAiSyncStatusService.recompute(event.runId)
        }
    }
}
