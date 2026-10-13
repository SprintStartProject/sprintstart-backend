package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Claims due Notion connections and advances their schedules before execution. */
@Service
internal class NotionConnectionScheduleService(
    private val connectionRepository: NotionWorkspaceConnectionRepository,
    private val scheduleCalculator: NotionScheduleCalculator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Tracked("Retrieving all Notion connections due for sync now")
    fun claimDueConnections(now: Instant): List<NotionScheduledConnection> {
        return connectionRepository
            .findAllByAutoUpdateTrueAndSourceEnabledTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(now)
            .mapNotNull { connection ->
                val nextSyncAt = scheduleCalculator.calculateNextSyncAt(connection.schedule, now)
                connection.nextSyncAt = nextSyncAt
                if (nextSyncAt == null) {
                    logger.warn("Disabling invalid schedule for Notion connection {}", connection.id)
                    connection.autoUpdate = false
                    null
                } else {
                    NotionScheduledConnection(
                        connectionId = connection.id,
                        projectId = connection.projectId,
                    )
                }
            }
    }
}

internal data class NotionScheduledConnection(
    val connectionId: UUID,
    val projectId: UUID,
)
