package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.projects.BitbucketRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

class BitbucketRepositoryProjectServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val userApi = mockk<UserApi>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = BitbucketRepositoryProjectService(
        connectionRepository = connectionRepository,
        userApi = userApi,
        eventPublisher = eventPublisher,
    )

    private val repositoryId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val otherProjectId = UUID.randomUUID()

    @Test
    fun `links a project and announces the change`() {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection())
        every { connectionRepository.save(any()) } answers { firstArg() }

        val result = service.addProjectToRepository("auth-id", repositoryId, projectId)

        assertThat(result).containsExactly(projectId)
        verify {
            eventPublisher.publishEvent(
                BitbucketRepositoryProjectLinkChangedEvent(
                    workspace = "sprintstart",
                    slug = "backend",
                    projectId = projectId,
                    linked = true,
                ),
            )
        }
    }

    /** The announcement is what re-scopes the stored artifacts, so a repeat must repair it. */
    @Test
    fun `re-linking an already linked project still announces the change`() {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { connectionRepository.findById(repositoryId) } returns
            Optional.of(connection(projectIds = mutableSetOf(projectId)))
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.addProjectToRepository("auth-id", repositoryId, projectId)

        verify {
            eventPublisher.publishEvent(
                match<BitbucketRepositoryProjectLinkChangedEvent> { it.linked },
            )
        }
    }

    @Test
    fun `unlinks a project and announces the removal`() {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { connectionRepository.findById(repositoryId) } returns
            Optional.of(connection(projectIds = mutableSetOf(projectId, otherProjectId)))
        every { connectionRepository.save(any()) } answers { firstArg() }

        val result = service.removeProjectFromRepository("auth-id", repositoryId, projectId)

        assertThat(result).containsExactly(otherProjectId)
        verify {
            eventPublisher.publishEvent(
                match<BitbucketRepositoryProjectLinkChangedEvent> { !it.linked },
            )
        }
    }

    @Test
    fun `unlinking an unlinked project is idempotent`() {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        val existing = connection(projectIds = mutableSetOf(otherProjectId))
        every { connectionRepository.findById(repositoryId) } returns Optional.of(existing)
        every { connectionRepository.save(any()) } answers { firstArg() }

        val result = service.removeProjectFromRepository("auth-id", repositoryId, projectId)

        assertThat(result).containsExactly(otherProjectId)
    }

    @Test
    fun `refuses a caller without access to the project`() {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns false

        assertThrows<BitbucketProjectAccessDeniedException> {
            service.addProjectToRepository("auth-id", repositoryId, projectId)
        }

        verify(exactly = 0) { connectionRepository.findById(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `refuses an unknown connection`() {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { connectionRepository.findById(repositoryId) } returns Optional.empty()

        assertThrows<BitbucketRepositoryConnectionNotFoundException> {
            service.addProjectToRepository("auth-id", repositoryId, projectId)
        }
    }

    private fun connection(projectIds: MutableSet<UUID> = mutableSetOf()) = BitbucketConnection(
        workspace = "sprintstart",
        slug = "backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
        projectIdsInternal = projectIds,
    )
}
