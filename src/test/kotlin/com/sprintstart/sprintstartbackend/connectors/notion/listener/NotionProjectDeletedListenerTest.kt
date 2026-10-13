package com.sprintstart.sprintstartbackend.connectors.notion.listener

import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionWorkspaceConnectionPersistenceService
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class NotionProjectDeletedListenerTest {
    private val persistenceService = mockk<NotionWorkspaceConnectionPersistenceService>()
    private val listener = NotionProjectDeletedListener(persistenceService)

    @Test
    fun `project deletion removes every Notion connection owned by it`() {
        val projectId = UUID.randomUUID()
        every { persistenceService.deleteAllForProject(projectId) } just Runs

        listener.on(ProjectDeletedEvent(projectId))

        verify(exactly = 1) { persistenceService.deleteAllForProject(projectId) }
    }
}
