package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionPageConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class NotionConnectionScheduleServiceTest {
    private val repository = mockk<NotionPageConnectionRepository>()
    private val calculator = mockk<NotionScheduleCalculator>()
    private val service = NotionConnectionScheduleService(repository, calculator)

    @Test
    fun `claims enabled due connection and advances next synchronization`() {
        val now = Instant.parse("2026-09-30T12:00:00Z")
        val next = Instant.parse("2026-09-30T12:30:00Z")
        val connection = connection().also { stored ->
            stored.autoUpdate = true
            stored.nextSyncAt = now
            stored.schedule = "0 */30 * * * *"
        }
        every {
            repository.findAllByAutoUpdateTrueAndSourceEnabledTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(now)
        } returns listOf(connection)
        every { calculator.calculateNextSyncAt(connection.schedule, now) } returns next

        val claimed = service.claimDueConnections(now)

        assertThat(claimed).containsExactly(NotionScheduledConnection(connection.id, connection.projectId))
        assertThat(connection.nextSyncAt).isEqualTo(next)
        assertThat(connection.autoUpdate).isTrue()
    }

    @Test
    fun `invalid persisted schedule is disabled and not claimed`() {
        val now = Instant.parse("2026-09-30T12:00:00Z")
        val connection = connection().also { stored ->
            stored.autoUpdate = true
            stored.nextSyncAt = now
            stored.schedule = "invalid"
        }
        every {
            repository.findAllByAutoUpdateTrueAndSourceEnabledTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(now)
        } returns listOf(connection)
        every { calculator.calculateNextSyncAt("invalid", now) } returns null

        val claimed = service.claimDueConnections(now)

        assertThat(claimed).isEmpty()
        assertThat(connection.autoUpdate).isFalse()
        assertThat(connection.nextSyncAt).isNull()
        verify(exactly = 1) { calculator.calculateNextSyncAt("invalid", now) }
    }

    private fun connection(): NotionPageConnection {
        return NotionPageConnection(
            projectId = UUID.randomUUID(),
            pageId = "page-1",
            pageTitle = "Engineering Runbook",
            pageUrl = "https://www.notion.so/page-1",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
    }
}
