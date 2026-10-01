package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionConnectionScheduleService
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionPageIngestionService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/** Polls due Notion page connections and launches their ingestion flow. */
@Component
internal class NotionScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val scheduleService: NotionConnectionScheduleService,
    private val ingestionService: NotionPageIngestionService,
) {
    @Scheduled(fixedRate = 60_000)
    fun tick() {
        scheduleService.claimDueConnections(Instant.now()).forEach { connection ->
            scheduledExecutor.launch("Updating Notion connection '${connection.connectionId}'") {
                ingestionService.ingest(connection.projectId, connection.connectionId)
            }
        }
    }
}
