package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.external.events.ArtifactsIndexedEvent
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Triggers automatic project industry evaluation upon completion of artifact indexing in the AI service.
 *
 * Runs asynchronously on [applicationScope] when [ArtifactsIndexedEvent] is received. Requests an
 * industry re-evaluation for each project associated with the indexed artifacts. Evaluation failures
 * are caught and logged per project so that one failure never prevents the remaining projects from
 * being processed or breaks the ingestion flow.
 */
@Component
class IngestionIndustryEventListener(
    private val projectIndustryApi: ProjectIndustryApi,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleArtifactsIndexed(event: ArtifactsIndexedEvent) {
        applicationScope.launch {
            for (projectId in event.projectIds) {
                try {
                    projectIndustryApi.evaluateIndustryAutomatically(projectId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to auto-evaluate industry for project {} after run {}",
                        projectId,
                        event.runId,
                        e,
                    )
                }
            }
        }
    }
}
