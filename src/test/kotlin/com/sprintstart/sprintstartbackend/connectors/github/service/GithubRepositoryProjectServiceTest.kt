package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class GithubRepositoryProjectServiceTest {
    private val githubRepositoryConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val userApi = mockk<UserApi>()
    private val service = GithubRepositoryProjectService(githubRepositoryConnectionRepository, userApi)

    private val authId = "auth-subject"

    @Test
    fun `adds the project to an existing connection without re-fetching`() {
        val repositoryId = UUID.randomUUID()
        val existingProjectId = UUID.randomUUID()
        val newProjectId = UUID.randomUUID()
        val connection = connection(mutableSetOf(existingProjectId))
        every { userApi.userHasAccessToProject(authId, newProjectId) } returns true
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(connection)
        val saved = slot<GithubRepositoryConnection>()
        every { githubRepositoryConnectionRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.addProjectToRepository(authId, repositoryId, newProjectId)

        assertThat(result).containsExactlyInAnyOrder(existingProjectId, newProjectId)
        assertThat(saved.captured.projectIds).containsExactlyInAnyOrder(existingProjectId, newProjectId)
    }

    @Test
    fun `is idempotent when the project is already linked`() {
        val repositoryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val connection = connection(mutableSetOf(projectId))
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(connection)
        every { githubRepositoryConnectionRepository.save(any()) } answers { firstArg() }

        val result = service.addProjectToRepository(authId, repositoryId, projectId)

        assertThat(result).containsExactly(projectId)
    }

    @Test
    fun `rejects with forbidden when the caller has no access to the target project`() {
        val repositoryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        assertThatThrownBy { service.addProjectToRepository(authId, repositoryId, projectId) }
            .isInstanceOf(ProjectAccessDeniedException::class.java)

        verify(exactly = 0) { githubRepositoryConnectionRepository.save(any()) }
    }

    @Test
    fun `returns not found when the repository connection does not exist`() {
        val repositoryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.empty()

        assertThatThrownBy { service.addProjectToRepository(authId, repositoryId, projectId) }
            .isInstanceOf(RepositoryNotFoundException::class.java)
    }

    @Test
    fun `removes the project from an existing connection`() {
        val repositoryId = UUID.randomUUID()
        val keptProjectId = UUID.randomUUID()
        val removedProjectId = UUID.randomUUID()
        val connection = connection(mutableSetOf(keptProjectId, removedProjectId))
        every { userApi.userHasAccessToProject(authId, removedProjectId) } returns true
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(connection)
        val saved = slot<GithubRepositoryConnection>()
        every { githubRepositoryConnectionRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.removeProjectFromRepository(authId, repositoryId, removedProjectId)

        assertThat(result).containsExactly(keptProjectId)
        assertThat(saved.captured.projectIds).containsExactly(keptProjectId)
    }

    @Test
    fun `remove is idempotent when the project is not linked`() {
        val repositoryId = UUID.randomUUID()
        val existingProjectId = UUID.randomUUID()
        val notLinkedProjectId = UUID.randomUUID()
        val connection = connection(mutableSetOf(existingProjectId))
        every { userApi.userHasAccessToProject(authId, notLinkedProjectId) } returns true
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(connection)
        every { githubRepositoryConnectionRepository.save(any()) } answers { firstArg() }

        val result = service.removeProjectFromRepository(authId, repositoryId, notLinkedProjectId)

        assertThat(result).containsExactly(existingProjectId)
    }

    @Test
    fun `remove rejects with forbidden when the caller has no access to the target project`() {
        val repositoryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        assertThatThrownBy { service.removeProjectFromRepository(authId, repositoryId, projectId) }
            .isInstanceOf(ProjectAccessDeniedException::class.java)

        verify(exactly = 0) { githubRepositoryConnectionRepository.save(any()) }
    }

    @Test
    fun `remove returns not found when the repository connection does not exist`() {
        val repositoryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.empty()

        assertThatThrownBy { service.removeProjectFromRepository(authId, repositoryId, projectId) }
            .isInstanceOf(RepositoryNotFoundException::class.java)
    }

    private fun connection(
        projectIds: MutableSet<UUID>,
    ): GithubRepositoryConnection =
        mockk {
            every { projectIdsInternal } returns projectIds
            // Mirror the entity's computed getter so it reflects mutations made during the test.
            every { this@mockk.projectIds } answers { projectIds.toSet() }
        }
}
