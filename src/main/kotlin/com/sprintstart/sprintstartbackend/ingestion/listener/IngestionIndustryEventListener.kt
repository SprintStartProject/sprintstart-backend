package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

/**
 * Triggers automatic project industry evaluation upon completion of an ingestion run.
 *
 * Runs asynchronously after the transaction that completed the ingestion run has committed
 * (AFTER_COMMIT). Resolves all affected projects from the run's artifacts and requests an
 * industry re-evaluation for each. Evaluation failures are logged and never break or roll back
 * the ingestion flow.
 */
@Component
class IngestionIndustryEventListener(
    private val projectIndustryApi: ProjectIndustryApi,
    private val artifactRepository: ArtifactRepository,
    private val ingestionRunRepository: IngestionRunRepository,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRunFinished(event: RunFinishedEvent) {
        applicationScope.launch {
            try {
                val projectIds = resolveProjectIds(event.runId)
                for (projectId in projectIds) {
                    projectIndustryApi.evaluateIndustryAutomatically(projectId)
                }
            } catch (e: Exception) {
                logger.warn("Failed to trigger industry auto-evaluation for run {}", event.runId, e)
            }
        }
    }

    private fun resolveProjectIds(runId: UUID): Set<UUID> {
        val reingested: Set<UUID> = ingestionRunRepository
            .findWithAiSyncArtifactIdsById(runId)
            .map { it.artifactIdsToReingest.toSet() }
            .orElse(emptySet())

        return artifactRepository.findProjectIdsByIngestionRunId(runId) +
            if (reingested.isEmpty()) emptySet() else artifactRepository.findProjectIdsByArtifactIdIn(reingested)
    }
}
