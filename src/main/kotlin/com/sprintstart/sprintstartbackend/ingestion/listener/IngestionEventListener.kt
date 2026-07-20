package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
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
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handles a completed ingestion run event by scheduling artifact synchronization with the AI service.
     *
     * Runs on a fire-and-forget coroutine, so nothing upstream awaits this -- the run's
     * `aiSyncStatus` is the only place this outcome is recorded. Without the explicit
     * catch here, a failed sync would previously vanish into [applicationScope]'s
     * uncaught-exception handling with no link back to the run it belonged to.
     *
     * @param event The run-finished event containing the ingestion run id to synchronize.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRunFinished(
        event: RunFinishedEvent,
    ) {
        applicationScope.launch {
            try {
                runArtifactsIngestionService.ingestRunArtifacts(event.runId)
                ingestionRunLifeCycleService.markAiSyncSucceeded(event.runId)
            } catch (e: Exception) {
                logger.error("AI sync failed for ingestion run {}", event.runId, e)
                ingestionRunLifeCycleService.markAiSyncFailed(event.runId, e.message)
            }
        }
    }
}
