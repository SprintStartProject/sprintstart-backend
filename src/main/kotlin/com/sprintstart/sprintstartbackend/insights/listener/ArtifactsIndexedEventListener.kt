package com.sprintstart.sprintstartbackend.insights.listener

import com.sprintstart.sprintstartbackend.ingestion.external.events.ArtifactsIndexedEvent
import com.sprintstart.sprintstartbackend.insights.service.KnowledgeGapsAutoRefreshService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Rescans knowledge gaps for every project whose documentation just became searchable.
 *
 * Scheduling only — the scan itself runs on its own coroutine after a debounce window, so nothing
 * about the ingestion pipeline waits on it.
 */
@Component
class ArtifactsIndexedEventListener(
    private val knowledgeGapsAutoRefreshService: KnowledgeGapsAutoRefreshService,
) {
    @EventListener
    fun handleArtifactsIndexed(event: ArtifactsIndexedEvent) {
        event.projectIds.forEach(knowledgeGapsAutoRefreshService::scheduleRefresh)
    }
}
