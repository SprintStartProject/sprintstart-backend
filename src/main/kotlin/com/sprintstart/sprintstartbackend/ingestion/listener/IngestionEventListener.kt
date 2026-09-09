package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.events.ArtifactsIndexedEvent
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import com.sprintstart.sprintstartbackend.ingestion.service.RunArtifactsIngestionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

@Component
class IngestionEventListener(
    private val runArtifactsIngestionService: RunArtifactsIngestionService,
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
    private val artifactRepository: ArtifactRepository,
    private val ingestionRunRepository: IngestionRunRepository,
    private val publisher: ApplicationEventPublisher,
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
                announceIndexed(event.runId)
            } catch (e: Exception) {
                logger.error("AI sync failed for ingestion run {}", event.runId, e)
                ingestionRunLifeCycleService.markAiSyncFailed(event.runId, e.message)
            }
        }
    }

    /**
     * Defines action performed after application startup.
     *
     * In this case, we want to clean up the state after application startup. That means, all ingestion runs that still
     * have the [com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus.RUNNING] are marked as
     * failed, because if the run was still running on application close, it ended ungracefully.
     */
    @EventListener(ApplicationStartedEvent::class)
    fun handleApplicationStartedEvent() {
        val staleRuns = ingestionRunRepository.findAllRunning()
        for (run in staleRuns) {
            ingestionRunLifeCycleService.markSyncFailed(run, "Status running on application startup")
        }
    }

    /**
     * Tells the rest of the application that the run's artifacts are now searchable.
     *
     * Published only after the sync succeeded, because that is the first moment a consumer asking
     * the AI service about these documents would actually find them. A run that ingested nothing
     * is skipped: there is no project to announce and nothing downstream would change.
     *
     * Failures here are logged rather than propagated — the sync itself worked, and marking it
     * failed because a downstream notification did not go out would misreport the run.
     */
    private fun announceIndexed(runId: UUID) {
        try {
            val projectIds = artifactRepository.findProjectIdsByIngestionRunId(runId)
            if (projectIds.isEmpty()) return
            publisher.publishEvent(ArtifactsIndexedEvent(runId = runId, projectIds = projectIds))
        } catch (e: Exception) {
            logger.warn("Could not announce indexed artifacts for ingestion run {}", runId, e)
        }
    }
}
