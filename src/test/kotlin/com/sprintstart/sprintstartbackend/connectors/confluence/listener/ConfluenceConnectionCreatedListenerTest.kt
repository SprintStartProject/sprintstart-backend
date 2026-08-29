package com.sprintstart.sprintstartbackend.connectors.confluence.listener

import com.sprintstart.sprintstartbackend.connectors.confluence.event.ConfluenceConnectionCreatedEvent
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ConfluenceConnectionCreatedListenerTest {
    private val testScope = TestScope()
    private val ingestionService = mockk<ConfluencePageIngestionService>()
    private val listener =
        ConfluenceConnectionCreatedListener(
            ScheduledExecutor(testScope),
            ingestionService,
        )

    @Test
    fun `connection creation launches initial ingestion in background`() {
        val event = ConfluenceConnectionCreatedEvent(UUID.randomUUID(), UUID.randomUUID())
        coEvery { ingestionService.ingest(event.projectId, event.connectionId) } returns mockk()

        listener.handleConnectionCreated(event)
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { ingestionService.ingest(event.projectId, event.connectionId) }
    }

    @Test
    fun `listener runs only after connection transaction commits`() {
        val method = ConfluenceConnectionCreatedListener::class.java.getDeclaredMethod(
            "handleConnectionCreated",
            ConfluenceConnectionCreatedEvent::class.java,
        )
        val annotation = method.getAnnotation(TransactionalEventListener::class.java)

        assertThat(annotation).isNotNull
        assertThat(annotation.phase).isEqualTo(TransactionPhase.AFTER_COMMIT)
    }
}
