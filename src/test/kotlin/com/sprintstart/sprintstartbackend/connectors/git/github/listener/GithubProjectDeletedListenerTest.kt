package com.sprintstart.sprintstartbackend.connectors.git.github.listener

import com.sprintstart.sprintstartbackend.connectors.git.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubProjectDeletedListenerTest {
    private val api = mockk<GithubRepositoryApi>()
    private val listener = GithubProjectDeletedListener(api)

    @Test
    fun `project deletion unlinks the project from every GitHub repository connection`() {
        val projectId = UUID.randomUUID()
        every { api.removeProjectFromAllRepositories(projectId) } just Runs

        listener.on(ProjectDeletedEvent(projectId))

        verify(exactly = 1) { api.removeProjectFromAllRepositories(projectId) }
    }
}
