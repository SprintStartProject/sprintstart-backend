package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionConnectionScheduleService
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionWorkspaceIngestionService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Polls due workspace connections only after startup migration runners have completed.
 *
 * Each claimed connection advances its next schedule before asynchronous ingestion starts, which
 * prevents the minute poller from launching duplicate runs for the same due timestamp.
 */
@Component
internal class NotionScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val scheduleService: NotionConnectionScheduleService,
    private val ingestionService: NotionWorkspaceIngestionService,
) {
    @Volatile
    private var ready = false

    @EventListener(ApplicationReadyEvent::class)
    fun enableScheduling() {
        ready = true
    }

    @Scheduled(fixedRate = 60_000)
    fun tick() {
        if (!ready) return
        scheduleService.claimDueConnections(Instant.now()).forEach { connection ->
            scheduledExecutor.launch("Updating Notion connection '${connection.connectionId}'") {
                ingestionService.ingest(connection.projectId, connection.connectionId)
            }
        }
    }
}
