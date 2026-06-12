package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userService: UserService = UserService(userRepository)

    @Test
    fun `createUser should save and return created user`() {
        // give
        val request = CreateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val savedUserSlot = slot<User>()

        every {
            userRepository.save(capture(savedUserSlot))
        } answers {
            savedUserSlot.captured
        }

        // when
        val response = userService.createUser(request)

        // then
        verify(exactly = 1) {
            userRepository.save(any())
        }

        val savedUser = savedUserSlot.captured

        assertThat(savedUser.id).isNotNull()
        assertThat(savedUser.username).isEqualTo("max_backend")
        assertThat(savedUser.firstname).isEqualTo("Max")
        assertThat(savedUser.lastname).isEqualTo("Backend")
        assertThat(savedUser.primaryRole).isEqualTo(Role.NO_ROLE)
        assertThat(savedUser.secondaryRole).isEqualTo(Role.NO_ROLE)
        assertThat(savedUser.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)

        // Testing the mapping entity -> dto
        assertUserMatchesResponse(savedUser, response)
    }

    @Test
    fun `getAllUsers should return mapped users`() {
        // given
        val user1 = User(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val user2 = User(
            username = "anna_frontend",
            firstname = "Anna",
            lastname = "Frontend",
            workingArea = WorkingArea.FRONTEND_DEV,
        )

        every {
            userRepository.findAll()
        } returns listOf(user1, user2)

        // when
        val response = userService.getAllUsers()

        // then
        verify(exactly = 1) {
            userRepository.findAll()
        }

        assertThat(response).hasSize(2)
        assertThat(response).anySatisfy { assertUserMatchesResponse(user1, it) }
        assertThat(response).anySatisfy { assertUserMatchesResponse(user2, it) }
    }

    @Test
    fun `getAllUsers should return empty list`() {
        // given
        every {
            userRepository.findAll()
        } returns emptyList()

        // when
        val response = userService.getAllUsers()

        // then
        verify(exactly = 1) {
            userRepository.findAll()
        }

        assertThat(response).isEmpty()
    }

    @Test
    fun `getUserById should return user when found`() {
        // given
        val user = User(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        // when
        val response = userService.getUserById(user.id)

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `getUserById should throw NOT_FOUND when missing`() {
        // given
        val userId = UUID.randomUUID()

        every {
            userRepository.findById(userId)
        } returns Optional.empty()

        // when
        val exception = assertThrows<ResponseStatusException> {
            userService.getUserById(userId)
        }

        // then
        verify(exactly = 1) {
            userRepository.findById(userId)
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(exception.reason).isEqualTo("User with id: $userId not found")
    }

    @Test
    fun `updateUserById should update and return user`() {
        val user = User(
            username = "old_username",
            firstname = "Old",
            lastname = "Username",
            workingArea = WorkingArea.NO_WORKING_AREA,
        )

        val request = UpdateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.NO_ROLE,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        every {
            userRepository.save(user)
        } returns user

        // when
        val response = userService.updateUserById(user.id, request)

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        verify(exactly = 1) {
            userRepository.save(user)
        }

        // check if it updated correctly
        assertThat(user.username).isEqualTo("max_backend")
        assertThat(user.firstname).isEqualTo("Max")
        assertThat(user.lastname).isEqualTo("Backend")
        assertThat(user.primaryRole).isEqualTo(Role.EXISTING_MEMBER)
        assertThat(user.secondaryRole).isEqualTo(Role.NO_ROLE)
        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)

        // check if it mapped correctly
        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `updateUserById should throw NOT_FOUND when missing`() {
        // given
        val userId = UUID.randomUUID()

        val request = UpdateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.NO_ROLE,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(userId)
        } returns Optional.empty()

        // when
        val exception = assertThrows<ResponseStatusException> {
            userService.updateUserById(userId, request)
        }

        // then
        verify(exactly = 1) {
            userRepository.findById(userId)
        }

        verify(exactly = 0) {
            userRepository.save(any())
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(exception.reason).isEqualTo("User with id: $userId not found")
    }

    @Test
    fun `updateUserById should throw BAD_REQUEST when secondary role is set without primary role`() {
        // given
        val user = User(
            username = "old_username",
            firstname = "Old",
            lastname = "Username",
            workingArea = WorkingArea.NO_WORKING_AREA,
        )

        val request = UpdateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.NO_ROLE,
            secondaryRole = Role.EXISTING_MEMBER,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        // when
        val exception = assertThrows<ResponseStatusException> {
            userService.updateUserById(user.id, request)
        }

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        verify(exactly = 0) {
            userRepository.save(any())
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.reason).isEqualTo("Secondary role cannot be set without a primary role")
    }

    @Test
    fun `patchUserById should update and return user`() {
        val user = User(
            username = "max_backend",
            firstname = "Old",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val request = PatchUserRequest(
            firstname = "Max",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        every {
            userRepository.save(user)
        } returns user

        // when
        val response = userService.patchUserById(user.id, request)

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        verify(exactly = 1) {
            userRepository.save(user)
        }

        assertThat(user.username).isEqualTo("max_backend")
        assertThat(user.firstname).isEqualTo("Max")
        assertThat(user.lastname).isEqualTo("Backend")
        assertThat(user.primaryRole).isEqualTo(Role.EXISTING_MEMBER)
        assertThat(user.secondaryRole).isEqualTo(Role.ADMIN)
        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)

        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `patchUserById should throw NOT_FOUND when missing`() {
        // given
        val user = User(
            username = "max_backend",
            firstname = "Old",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val request = PatchUserRequest(
            firstname = "Max",
            secondaryRole = Role.ADMIN,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        // when
        val exception = assertThrows<ResponseStatusException> {
            userService.patchUserById(user.id, request)
        }

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        verify(exactly = 0) {
            userRepository.save(any())
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.reason).isEqualTo("Secondary role cannot be set without a primary role")
    }

    @Test
    fun `patchUserById should throw BAD_REQUEST when secondary role is set without primary role`() {
        val user = User(
            username = "max_backend",
            firstname = "Old",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val request = PatchUserRequest(
            firstname = "Max",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        every {
            userRepository.save(user)
        } returns user

        // when
        val response = userService.patchUserById(user.id, request)

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        verify(exactly = 1) {
            userRepository.save(user)
        }

        assertThat(user.username).isEqualTo("max_backend")
        assertThat(user.firstname).isEqualTo("Max")
        assertThat(user.lastname).isEqualTo("Backend")
        assertThat(user.primaryRole).isEqualTo(Role.EXISTING_MEMBER)
        assertThat(user.secondaryRole).isEqualTo(Role.ADMIN)
        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)

        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `deleteUserById should delete existing user`() {
        val user = User(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        every {
            userRepository.delete(user)
        } returns Unit

        // when
        userService.deleteUserById(user.id)

        // then
        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        verify(exactly = 1) {
            userRepository.delete(user)
        }
    }

    @Test
    fun `deleteUserById should throw NOT_FOUND when missing`() {
        val userId = UUID.randomUUID()

        every {
            userRepository.findById(userId)
        } returns Optional.empty()

        // when
        val exception = assertThrows<ResponseStatusException> {
            userService.deleteUserById(userId)
        }

        // then
        verify(exactly = 1) {
            userRepository.findById(userId)
        }

        verify(exactly = 0) {
            userRepository.delete(any())
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(exception.reason).isEqualTo("User with id: $userId not found")
    }

    // Helpers

    private fun assertUserMatchesResponse(user: User, response: GetUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.primaryRole).isEqualTo(user.primaryRole)
        assertThat(response.secondaryRole).isEqualTo(user.secondaryRole)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }

    private fun assertUserMatchesResponse(user: User, response: CreateUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.primaryRole).isEqualTo(user.primaryRole)
        assertThat(response.secondaryRole).isEqualTo(user.secondaryRole)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }

    private fun assertUserMatchesResponse(user: User, response: UpdateUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.primaryRole).isEqualTo(user.primaryRole)
        assertThat(response.secondaryRole).isEqualTo(user.secondaryRole)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }

    private fun assertUserMatchesResponse(user: User, response: PatchUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.primaryRole).isEqualTo(user.primaryRole)
        assertThat(response.secondaryRole).isEqualTo(user.secondaryRole)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }
}
