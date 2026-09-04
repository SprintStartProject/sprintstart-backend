package com.sprintstart.sprintstartbackend.connectors.confluence.listener

import com.sprintstart.sprintstartbackend.connectors.confluence.event.ConfluenceConnectionCreatedEvent
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** Starts initial Confluence ingestion only after connection persistence has committed. */
@Component
internal class ConfluenceConnectionCreatedListener(
    private val scheduledExecutor: ScheduledExecutor,
    private val ingestionService: ConfluencePageIngestionService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleConnectionCreated(event: ConfluenceConnectionCreatedEvent) {
        scheduledExecutor.launch("Initial ingestion for Confluence connection '${event.connectionId}'") {
            ingestionService.ingest(event.projectId, event.connectionId)
        }
    }
}
