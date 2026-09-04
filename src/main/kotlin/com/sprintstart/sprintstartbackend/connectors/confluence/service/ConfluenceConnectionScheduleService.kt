package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Claims due Confluence connections and advances their schedules before execution. */
@Service
internal class ConfluenceConnectionScheduleService(
    private val connectionRepository: ConfluenceSpaceConnectionRepository,
    private val scheduleCalculator: ConfluenceScheduleCalculator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun claimDueConnections(now: Instant): List<ConfluenceScheduledConnection> {
        return connectionRepository
            .findAllByAutoUpdateTrueAndSourceEnabledTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(now)
            .mapNotNull { connection ->
                val nextSyncAt = scheduleCalculator.calculateNextSyncAt(connection.schedule, now)
                connection.nextSyncAt = nextSyncAt
                if (nextSyncAt == null) {
                    logger.warn("Disabling invalid schedule for Confluence connection {}", connection.id)
                    connection.autoUpdate = false
                    null
                } else {
                    ConfluenceScheduledConnection(
                        connectionId = connection.id,
                        projectId = connection.projectId,
                    )
                }
            }
    }
}

internal data class ConfluenceScheduledConnection(
    val connectionId: UUID,
    val projectId: UUID,
)
