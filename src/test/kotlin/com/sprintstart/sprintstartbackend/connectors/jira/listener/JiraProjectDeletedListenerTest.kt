package com.sprintstart.sprintstartbackend.connectors.jira.listener

import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class JiraProjectDeletedListenerTest {
    private val api = mockk<JiraInstanceApi>()
    private val listener = JiraProjectDeletedListener(api)

    @Test
    fun `project deletion unlinks the project from every Jira instance`() {
        val projectId = UUID.randomUUID()
        every { api.removeProjectFromAllInstances(projectId) } just Runs

        listener.on(ProjectDeletedEvent(projectId))

        verify(exactly = 1) { api.removeProjectFromAllInstances(projectId) }
    }
}
