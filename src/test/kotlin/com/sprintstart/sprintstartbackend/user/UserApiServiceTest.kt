package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.GithubLoginService
import com.sprintstart.sprintstartbackend.user.service.UserApiService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class UserApiServiceTest {
    private val userRepository: UserRepository = mockk()
    private val projectRepository: ProjectRepository = mockk()

    // The real service, not a mock: it owns normalisation, the uniqueness rule and clearing a
    // stale verification verdict, and setGithubLogin exists precisely to delegate to it.
    private val githubLoginService = GithubLoginService(userRepository)
    private val userApi: UserApi = UserApiService(userRepository, projectRepository, githubLoginService)

    /**
     * The buddy is a second *entry point* for a GitHub login, never a second writer. If this ever
     * stopped delegating, the conversation path would quietly lose normalisation, the uniqueness
     * check, and the rule that changing a login discards the verdict about the old one.
     */
    @Test
    fun `setGithubLogin stores what GithubLoginService normalises, not what was passed`() {
        val userId = UUID.randomUUID()
        val user = User(
            authId = "auth-1",
            username = "alice",
            email = null,
            firstname = "Alice",
            lastname = "A",
        )
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.existsByGithubLoginAndIdNot(any(), any()) } returns false
        every { userRepository.save(user) } returns user

        val stored = userApi.setGithubLogin(userId, "  OctoCat  ")

        assertThat(stored).isEqualTo("octocat")
        assertThat(user.githubLogin).isEqualTo("octocat")
    }

    @Test
    fun `setGithubLogin refuses a username another user already claims`() {
        val userId = UUID.randomUUID()
        val user = User(
            authId = "auth-1",
            username = "alice",
            email = null,
            firstname = "Alice",
            lastname = "A",
        )
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.existsByGithubLoginAndIdNot("octocat", user.id) } returns true

        assertThrows<ResponseStatusException> { userApi.setGithubLogin(userId, "octocat") }
            .also { assertThat(it.statusCode.value()).isEqualTo(409) }
    }

    @Test
    fun `exists should return true when user exists`() {
        // given
        val userId = UUID.randomUUID()

        every {
            userRepository.existsById(userId)
        } returns true

        // when
        val result = userApi.exists(userId)

        // then
        verify(exactly = 1) {
            userRepository.existsById(userId)
        }

        assertThat(result).isTrue()
    }

    @Test
    fun `exists should return false when user does not exists`() {
        // given
        val userId = UUID.randomUUID()

        every {
            userRepository.existsById(userId)
        } returns false

        // when
        val result = userApi.exists(userId)

        // then
        verify(exactly = 1) {
            userRepository.existsById(userId)
        }

        assertThat(result).isFalse()
    }

    @Test
    fun `getUserIdByAuthId should return user id when auth id exists`() {
        // given
        val userId = UUID.randomUUID()
        every {
            userRepository.findIdByAuthId("auth-1")
        } returns Optional.of(userId)

        // when
        val result = userApi.getUserIdByAuthId("auth-1")

        // then
        verify(exactly = 1) {
            userRepository.findIdByAuthId("auth-1")
        }
        assertThat(result).contains(userId)
    }

    @Test
    fun `getUserIdByAuthId should return empty when auth id does not exist`() {
        // given
        every {
            userRepository.findIdByAuthId("missing-auth")
        } returns Optional.empty()

        // when
        val result = userApi.getUserIdByAuthId("missing-auth")

        // then
        verify(exactly = 1) {
            userRepository.findIdByAuthId("missing-auth")
        }
        assertThat(result).isEmpty()
    }

    @Test
    fun `userHasAccessToProject should return true when user belongs to project`() {
        val projectId = UUID.randomUUID()
        val user = user(project = Project(id = projectId, name = "Project"))

        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)

        val result = userApi.userHasAccessToProject("auth-1", projectId)

        assertThat(result).isTrue()
    }

    @Test
    fun `userHasAccessToProject should return true when user is admin`() {
        val requestedProjectId = UUID.randomUUID()
        val user = user(project = null).apply { roles.add(Role.ADMIN) }

        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)

        val result = userApi.userHasAccessToProject("auth-1", requestedProjectId)

        assertThat(result).isTrue()
    }

    @Test
    fun `userHasAccessToProject should return false when user belongs to another project`() {
        val requestedProjectId = UUID.randomUUID()
        val user = user(project = Project(id = UUID.randomUUID(), name = "Project"))

        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every { projectRepository.findManagerAuthId(requestedProjectId) } returns Optional.empty()

        val result = userApi.userHasAccessToProject("auth-1", requestedProjectId)

        assertThat(result).isFalse()
    }

    @Test
    fun `userHasAccessToProject should return true when user manages the project without membership`() {
        val managedProjectId = UUID.randomUUID()
        val user = user(project = null)

        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every { projectRepository.findManagerAuthId(managedProjectId) } returns Optional.of("auth-1")

        val result = userApi.userHasAccessToProject("auth-1", managedProjectId)

        assertThat(result).isTrue()
    }

    @Test
    fun `userHasAccessToProject should return false when another user manages the project`() {
        val managedProjectId = UUID.randomUUID()
        val user = user(project = null)

        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every { projectRepository.findManagerAuthId(managedProjectId) } returns Optional.of("auth-2")

        val result = userApi.userHasAccessToProject("auth-1", managedProjectId)

        assertThat(result).isFalse()
    }

    @Test
    fun `userHasAccessToProject should return false when user does not exist`() {
        every { userRepository.findByAuthId("missing-auth") } returns Optional.empty()

        val result = userApi.userHasAccessToProject("missing-auth", UUID.randomUUID())

        assertThat(result).isFalse()
    }

    private fun user(project: Project?) = User(
        authId = "auth-1",
        username = "alice",
        email = "alice@example.com",
        firstname = "Alice",
        lastname = "Doe",
        projects = project?.let { mutableSetOf(it) } ?: mutableSetOf(),
    )
}
