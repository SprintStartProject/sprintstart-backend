package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.listener

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class BitbucketProjectDeletedListenerTest {
    private val api = mockk<BitbucketRepositoryApi>()
    private val listener = BitbucketProjectDeletedListener(api)

    @Test
    fun `project deletion unlinks the project from every Bitbucket repository connection`() {
        val projectId = UUID.randomUUID()
        every { api.removeProjectFromAllRepositories(projectId) } just Runs

        listener.on(ProjectDeletedEvent(projectId))

        verify(exactly = 1) { api.removeProjectFromAllRepositories(projectId) }
    }
}
