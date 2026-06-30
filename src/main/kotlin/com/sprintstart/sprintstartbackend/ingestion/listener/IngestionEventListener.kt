package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.RunArtifactsIngestionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class IngestionEventListener(
    private val runArtifactsIngestionService: RunArtifactsIngestionService,
    private val applicationScope: CoroutineScope,
) {
    @EventListener
    fun handleRunFinished(
        event: RunFinishedEvent,
    ) {
        applicationScope.launch {
            runArtifactsIngestionService.ingestRunArtifacts(event.runId)
        }
    }
}
