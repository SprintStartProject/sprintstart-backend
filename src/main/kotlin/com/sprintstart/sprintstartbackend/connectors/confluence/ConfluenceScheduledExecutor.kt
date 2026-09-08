package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionScheduleService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/** Polls for due Confluence connections and launches their existing ingestion flow. */
@Component
internal class ConfluenceScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val scheduleService: ConfluenceConnectionScheduleService,
    private val ingestionService: ConfluencePageIngestionService,
) {
    @Scheduled(fixedRate = 60_000)
    fun tick() {
        scheduleService.claimDueConnections(Instant.now()).forEach { connection ->
            scheduledExecutor.launch("Updating Confluence connection '${connection.connectionId}'") {
                ingestionService.ingest(connection.projectId, connection.connectionId)
            }
        }
    }
}
