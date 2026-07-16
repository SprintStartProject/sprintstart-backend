package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserApiService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class UserApiServiceTest {
    private val userRepository: UserRepository = mockk()
    private val projectRepository: ProjectRepository = mockk()
    private val userApi: UserApi = UserApiService(userRepository, projectRepository)

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
    fun `userHasAccessToProject should return false when user belongs to another project`() {
        val requestedProjectId = UUID.randomUUID()
        val user = user(project = Project(id = UUID.randomUUID(), name = "Project"))

        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)

        val result = userApi.userHasAccessToProject("auth-1", requestedProjectId)

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
