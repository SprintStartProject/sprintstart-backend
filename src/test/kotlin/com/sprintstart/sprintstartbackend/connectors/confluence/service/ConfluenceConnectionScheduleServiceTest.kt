package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfluenceConnectionScheduleServiceTest {
    private val repository = mockk<ConfluenceSpaceConnectionRepository>()
    private val calculator = mockk<ConfluenceScheduleCalculator>()
    private val service = ConfluenceConnectionScheduleService(repository, calculator)

    @Test
    fun `claims enabled due connection and advances next synchronization`() {
        val now = Instant.parse("2026-08-28T12:00:00Z")
        val next = Instant.parse("2026-08-28T12:30:00Z")
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

        assertThat(claimed).containsExactly(ConfluenceScheduledConnection(connection.id, connection.projectId))
        assertThat(connection.nextSyncAt).isEqualTo(next)
        assertThat(connection.autoUpdate).isTrue()
    }

    @Test
    fun `invalid persisted schedule is disabled and not claimed`() {
        val now = Instant.parse("2026-08-28T12:00:00Z")
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

    private fun connection(): ConfluenceSpaceConnection {
        return ConfluenceSpaceConnection(
            projectId = UUID.randomUUID(),
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "42",
            spaceKey = "ENG",
        )
    }
}
