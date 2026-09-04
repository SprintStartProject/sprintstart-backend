package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionScheduleService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceScheduledConnection
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
class ConfluenceScheduledExecutorTest {
    private val scheduleService = mockk<ConfluenceConnectionScheduleService>()
    private val ingestionService = mockk<ConfluencePageIngestionService>()
    private val applicationScope = TestScope(UnconfinedTestDispatcher())
    private val executor =
        ConfluenceScheduledExecutor(
            ScheduledExecutor(applicationScope),
            scheduleService,
            ingestionService,
        )

    @AfterEach
    fun tearDown() {
        applicationScope.cancel()
    }

    @Test
    fun `tick launches existing ingestion for every claimed connection`() {
        val connection = ConfluenceScheduledConnection(UUID.randomUUID(), UUID.randomUUID())
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
