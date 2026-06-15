package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.events.UserWorkingAreaUpdatedEvent
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val userService: UserService = UserService(userRepository, eventPublisher)

    // --- getAllUsers ---

    @Test
    fun `getAllUsers should return mapped list`() {
        val user1 = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        val user2 = user(authId = "auth-2", username = "bob", workingArea = WorkingArea.FRONTEND_DEV)
        every { userRepository.findAll() } returns listOf(user1, user2)

        val result = userService.getAllUsers()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.username }).containsExactlyInAnyOrder("alice", "bob")
        verify(exactly = 1) { userRepository.findAll() }
    }

    // --- getMe ---

    @Test
    fun `getMe should return user for given authId`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)

        val result = userService.getMe("auth-1")

        assertUserMatchesResponse(user, result)
        verify(exactly = 1) { userRepository.findByAuthId("auth-1") }
    }

    @Test
    fun `getMe should throw NOT_FOUND when user missing`() {
        every { userRepository.findByAuthId("missing") } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { userService.getMe("missing") }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- patchMe ---

    @Test
    fun `patchMe should update app-owned fields and return user`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.NO_WORKING_AREA)
        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every { userRepository.save(user) } returns user
        every { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) } just runs

        val result = userService.patchMe("auth-1", PatchMeRequest(workingArea = WorkingArea.BACKEND_DEV))

        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
        assertThat(user.username).isEqualTo("alice") // identity field unchanged
        assertUserMatchesResponse(user, result)
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                UserWorkingAreaUpdatedEvent(user.id, WorkingArea.NO_WORKING_AREA, WorkingArea.BACKEND_DEV),
            )
        }
    }

    @Test
    fun `patchMe should leave null fields unchanged`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        userService.patchMe("auth-1", PatchMeRequest(workingArea = null))

        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
        verify(exactly = 0) { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) }
    }

    @Test
    fun `patchMe should not publish event when working area stays the same`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        userService.patchMe("auth-1", PatchMeRequest(workingArea = WorkingArea.BACKEND_DEV))

        verify(exactly = 0) { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) }
    }

    @Test
    fun `patchMe should throw NOT_FOUND when user missing`() {
        every { userRepository.findByAuthId("missing") } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            userService.patchMe("missing", PatchMeRequest(workingArea = WorkingArea.BACKEND_DEV))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- getUserById ---

    @Test
    fun `getUserById should return user for given UUID`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findById(user.id) } returns Optional.of(user)

        val result = userService.getUserById(user.id)

        assertUserMatchesResponse(user, result)
    }

    @Test
    fun `getUserById should throw NOT_FOUND when missing`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { userService.getUserById(id) }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- updateUserById ---

    @Test
    fun `updateUserById should replace app-owned fields`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.NO_WORKING_AREA)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(user) } returns user
        every { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) } just runs

        val result = userService.updateUserById(user.id, UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV))

        assertThat(user.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
        assertThat(user.username).isEqualTo("alice") // identity field untouched
        assertThat(result.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                UserWorkingAreaUpdatedEvent(user.id, WorkingArea.NO_WORKING_AREA, WorkingArea.BACKEND_DEV),
            )
        }
    }

    @Test
    fun `updateUserById should not publish event when working area stays the same`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        userService.updateUserById(user.id, UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV))

        verify(exactly = 0) { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) }
    }

    @Test
    fun `updateUserById should throw NOT_FOUND when missing`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            userService.updateUserById(id, UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- patchUserById ---

    @Test
    fun `patchUserById should update only provided fields`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.NO_WORKING_AREA)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(user) } returns user
        every { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) } just runs

        userService.patchUserById(user.id, PatchUserRequest(workingArea = WorkingArea.FRONTEND_DEV))

        assertThat(user.workingArea).isEqualTo(WorkingArea.FRONTEND_DEV)
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                UserWorkingAreaUpdatedEvent(user.id, WorkingArea.NO_WORKING_AREA, WorkingArea.FRONTEND_DEV),
            )
        }
    }

    @Test
    fun `patchUserById should not publish event when working area stays the same`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.FRONTEND_DEV)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        userService.patchUserById(user.id, PatchUserRequest(workingArea = WorkingArea.FRONTEND_DEV))

        verify(exactly = 0) { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) }
    }

    @Test
    fun `patchUserById should throw NOT_FOUND when missing`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            userService.patchUserById(id, PatchUserRequest(workingArea = WorkingArea.BACKEND_DEV))
        }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- deleteUserById ---

    @Test
    fun `deleteUserById should delete user`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.delete(user) } returns Unit

        userService.deleteUserById(user.id)

        verify(exactly = 1) { userRepository.delete(user) }
    }

    @Test
    fun `deleteUserById should throw NOT_FOUND when missing`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { userService.deleteUserById(id) }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- helpers ---

    private fun user(
        authId: String,
        username: String,
        workingArea: WorkingArea,
    ) = User(
        authId = authId,
        username = username,
        email = "first.last@mail.de",
        firstname = "First",
        lastname = "Last",
        workingArea = workingArea,
    )

    private fun assertUserMatchesResponse(user: User, response: GetUserResponse) {
        assertThat(response.id).isEqualTo(user.id)
        assertThat(response.authId).isEqualTo(user.authId)
        assertThat(response.username).isEqualTo(user.username)
        assertThat(response.email).isEqualTo(user.email)
        assertThat(response.firstname).isEqualTo(user.firstname)
        assertThat(response.lastname).isEqualTo(user.lastname)
        assertThat(response.workingArea).isEqualTo(user.workingArea)
    }
}
