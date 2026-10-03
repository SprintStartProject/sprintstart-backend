package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionConnectionScheduleService
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionPageIngestionService
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionScheduledConnection
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NotionScheduledExecutorTest {
    private val scheduleService = mockk<NotionConnectionScheduleService>()
    private val ingestionService = mockk<NotionPageIngestionService>()
    private val applicationScope = TestScope(UnconfinedTestDispatcher())
    private val executor = NotionScheduledExecutor(
        ScheduledExecutor(applicationScope),
        scheduleService,
        ingestionService,
    )

    @AfterEach
    fun tearDown() {
        applicationScope.cancel()
    }

    @Test
    fun `tick launches ingestion for every claimed connection`() {
        val connection = NotionScheduledConnection(UUID.randomUUID(), UUID.randomUUID())
        every { scheduleService.claimDueConnections(any()) } returns listOf(connection)
        coEvery { ingestionService.ingest(connection.projectId, connection.connectionId) } returns mockk()

        executor.tick()

        coVerify(exactly = 1) { ingestionService.ingest(connection.projectId, connection.connectionId) }
    }

    @Test
    fun `tick performs no ingestion when nothing is due`() {
        every { scheduleService.claimDueConnections(any()) } returns emptyList()

        executor.tick()

        coVerify(exactly = 0) { ingestionService.ingest(any(), any()) }
    }
}
