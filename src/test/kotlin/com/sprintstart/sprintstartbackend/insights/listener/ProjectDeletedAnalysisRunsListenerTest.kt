package com.sprintstart.sprintstartbackend.insights.listener

import com.sprintstart.sprintstartbackend.insights.service.ProjectAnalysisRunService
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

class ProjectDeletedAnalysisRunsListenerTest {
    private val service: ProjectAnalysisRunService = mockk()
    private val listener = ProjectDeletedAnalysisRunsListener(service)
    private val projectId: UUID = UUID.randomUUID()

    @Test
    fun `a deleted project's analysis runs are deleted`() {
        every { service.deleteForProject(projectId) } returns 2

        listener.on(ProjectDeletedEvent(projectId))

        verify { service.deleteForProject(projectId) }
    }

    @Test
    fun `a failed cleanup is logged, not thrown`() {
        every { service.deleteForProject(projectId) } throws IllegalStateException("db down")

        assertDoesNotThrow { listener.on(ProjectDeletedEvent(projectId)) }
    }
}
