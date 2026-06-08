package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.SyncUserRequest
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

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userService: UserService = UserService(userRepository)

    @Test
    fun `createUser should save and return created user`() {
        val request = CreateUserRequest(
            authId = "keycloak-id-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val savedUserSlot = slot<User>()

        every {
            userRepository.existsByAuthId(request.authId)
        } returns false

        every {
            userRepository.save(capture(savedUserSlot))
        } answers {
            savedUserSlot.captured
        }

        val response = userService.createUser(request)

        verify(exactly = 1) {
            userRepository.existsByAuthId(request.authId)
            userRepository.save(any())
        }

        val savedUser = savedUserSlot.captured
        assertThat(savedUser.id).isNotNull()
        assertThat(savedUser.authId).isEqualTo(request.authId)
        assertThat(savedUser.username).isEqualTo(request.username)
        assertThat(savedUser.firstname).isEqualTo(request.firstname)
        assertThat(savedUser.lastname).isEqualTo(request.lastname)
        assertThat(savedUser.workingArea).isEqualTo(request.workingArea)
        assertUserMatchesResponse(savedUser, response)
    }

    @Test
    fun `createUser should throw BAD_REQUEST when auth id already exists`() {
        val request = CreateUserRequest(
            authId = "keycloak-id-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.existsByAuthId(request.authId)
        } returns true

        val exception = assertThrows<ResponseStatusException> {
            userService.createUser(request)
        }

        verify(exactly = 1) {
            userRepository.existsByAuthId(request.authId)
        }

        verify(exactly = 0) {
            userRepository.save(any())
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.reason).isEqualTo("User with authId: ${request.authId} already exists")
    }

    @Test
    fun `getAllUsers should return mapped users`() {
        val user1 = user(
            authId = "keycloak-id-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )
        val user2 = user(
            authId = "keycloak-id-2",
            username = "anna_frontend",
            firstname = "Anna",
            lastname = "Frontend",
            workingArea = WorkingArea.FRONTEND_DEV,
        )

        every {
            userRepository.findAll()
        } returns listOf(user1, user2)

        val response = userService.getAllUsers()

        verify(exactly = 1) {
            userRepository.findAll()
        }

        assertThat(response).hasSize(2)
        assertThat(response).anySatisfy { assertUserMatchesResponse(user1, it) }
        assertThat(response).anySatisfy { assertUserMatchesResponse(user2, it) }
    }

    @Test
    fun `getUserById should return user when found`() {
        val user = user(
            authId = "keycloak-id-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        val response = userService.getUserById(user.id)

        verify(exactly = 1) {
            userRepository.findById(user.id)
        }

        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `getUserByAuthId should return user when found`() {
        val user = user(
            authId = "keycloak-id-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findByAuthId(user.authId)
        } returns Optional.of(user)

        val response = userService.getUserByAuthId(user.authId)

        verify(exactly = 1) {
            userRepository.findByAuthId(user.authId)
        }

        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `getUserByAuthId should throw NOT_FOUND when missing`() {
        val authId = "missing-id"

        every {
            userRepository.findByAuthId(authId)
        } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            userService.getUserByAuthId(authId)
        }

        verify(exactly = 1) {
            userRepository.findByAuthId(authId)
        }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(exception.reason).isEqualTo("User with authId: $authId not found")
    }

    @Test
    fun `updateUserById should update and return user`() {
        val user = user(
            authId = "keycloak-id-1",
            username = "old_username",
            firstname = "Old",
            lastname = "Username",
            workingArea = WorkingArea.NO_WORKING_AREA,
        )

        val request = UpdateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        every {
            userRepository.save(user)
        } returns user

        val response = userService.updateUserById(user.id, request)

        verify(exactly = 1) {
            userRepository.findById(user.id)
            userRepository.save(user)
        }

        assertThat(user.authId).isEqualTo("keycloak-id-1")
        assertThat(user.username).isEqualTo(request.username)
        assertThat(user.firstname).isEqualTo(request.firstname)
        assertThat(user.lastname).isEqualTo(request.lastname)
        assertThat(user.workingArea).isEqualTo(request.workingArea)
        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `patchUserById should update and return user`() {
        val user = user(
            authId = "keycloak-id-1",
            username = "max_backend",
            firstname = "Old",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val request = PatchUserRequest(
            firstname = "Max",
            workingArea = WorkingArea.FRONTEND_DEV,
        )

        every {
            userRepository.findById(user.id)
        } returns Optional.of(user)

        every {
            userRepository.save(user)
        } returns user

        val response = userService.patchUserById(user.id, request)

        verify(exactly = 1) {
            userRepository.findById(user.id)
            userRepository.save(user)
        }

        assertThat(user.authId).isEqualTo("keycloak-id-1")
        assertThat(user.username).isEqualTo("max_backend")
        assertThat(user.firstname).isEqualTo("Max")
        assertThat(user.lastname).isEqualTo("Backend")
        assertThat(user.workingArea).isEqualTo(WorkingArea.FRONTEND_DEV)
        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `syncUser should update existing user by auth id`() {
        val user = user(
            authId = "keycloak-id-1",
            username = "old_username",
            firstname = "Old",
            lastname = "Name",
            workingArea = WorkingArea.BACKEND_DEV,
        )
        val request = SyncUserRequest(
            authId = "keycloak-id-1",
            username = "new_username",
            firstname = "New",
            lastname = "Name",
        )

        every {
            userRepository.findByAuthId(request.authId)
        } returns Optional.of(user)

        every {
            userRepository.save(user)
        } returns user

        val response = userService.syncUser(request)

        verify(exactly = 1) {
            userRepository.findByAuthId(request.authId)
            userRepository.save(user)
        }

        assertThat(user.username).isEqualTo(request.username)
        assertThat(user.firstname).isEqualTo(request.firstname)
        assertThat(user.lastname).isEqualTo(request.lastname)
        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
        assertUserMatchesResponse(user, response)
    }

    @Test
    fun `syncUser should create new user when auth id does not exist`() {
        val request = SyncUserRequest(
            authId = "keycloak-id-1",
            username = "new_username",
            firstname = "New",
            lastname = "Name",
        )

        val savedUserSlot = slot<User>()

        every {
            userRepository.findByAuthId(request.authId)
        } returns Optional.empty()

        every {
            userRepository.save(capture(savedUserSlot))
        } answers {
            savedUserSlot.captured
        }

        val response = userService.syncUser(request)

        verify(exactly = 1) {
            userRepository.findByAuthId(request.authId)
            userRepository.save(any())
        }

        val savedUser = savedUserSlot.captured
        assertThat(savedUser.authId).isEqualTo(request.authId)
        assertThat(savedUser.username).isEqualTo(request.username)
        assertThat(savedUser.firstname).isEqualTo(request.firstname)
        assertThat(savedUser.lastname).isEqualTo(request.lastname)
        assertThat(savedUser.workingArea).isEqualTo(WorkingArea.NO_WORKING_AREA)
        assertUserMatchesResponse(savedUser, response)
    }

    @Test
    fun `deleteUserById should delete existing user`() {
        val user = user(
            authId = "keycloak-id-1",
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

        userService.deleteUserById(user.id)

        verify(exactly = 1) {
            userRepository.findById(user.id)
            userRepository.delete(user)
        }
    }

    private fun user(
        authId: String,
        username: String,
        firstname: String,
        lastname: String,
        workingArea: WorkingArea,
    ): User {
        return User(
            authId = authId,
            username = username,
            firstname = firstname,
            lastname = lastname,
            workingArea = workingArea,
        )
    }

    private fun assertUserMatchesResponse(user: User, response: GetUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.authId).isEqualTo(user.authId)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }

    private fun assertUserMatchesResponse(user: User, response: CreateUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.authId).isEqualTo(user.authId)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }

    private fun assertUserMatchesResponse(user: User, response: UpdateUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.authId).isEqualTo(user.authId)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }

    private fun assertUserMatchesResponse(user: User, response: PatchUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.authId).isEqualTo(user.authId)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }
}
