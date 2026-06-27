package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.external.events.UserWorkingAreaUpdatedEvent
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserEnabledRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.KeycloakAdminClient
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val keycloakAdminClient: KeycloakAdminClient = mockk()
    private val userService = UserService(userRepository, eventPublisher, keycloakAdminClient)

    @Test
    fun `getMe returns extended user response`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        user.roles.add(Role.USER)
        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)

        val result = userService.getMe("auth-1")

        assertThat(result.firstName).isEqualTo("First")
        assertThat(result.workingArea).isEqualTo(WorkingArea.BACKEND_DEV)
        assertThat(result.permissionGroup).isEqualTo(Role.USER)
        assertThat(result.enabled).isTrue()
    }

    @Test
    fun `getAllUsers returns local projections without Keycloak calls`() {
        val keycloakUser = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        keycloakUser.roles.add(Role.USER)
        every { userRepository.findAll() } returns listOf(keycloakUser)

        val result = userService.getAllUsers()

        assertThat(result).hasSize(1)
        assertThat(result.single().authId).isEqualTo("auth-1")
        verify(exactly = 0) { keycloakAdminClient.getPermissionGroups(any()) }
    }

    @Test
    fun `patchMe forwards identity fields to Keycloak and updates local projection`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        val request = PatchMeRequest(
            email = "new@mail.de",
            firstName = "Alicia",
            profileIcon = "icon-star",
            workingArea = WorkingArea.FRONTEND_DEV,
        )
        every { userRepository.findByAuthId("auth-1") } returns Optional.of(user)
        every {
            keycloakAdminClient.updateUserProfile("auth-1", "new@mail.de", "Alicia", null)
        } just runs
        every { userRepository.save(user) } returns user
        every { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) } just runs

        val result = userService.patchMe("auth-1", request)

        assertThat(user.email).isEqualTo("new@mail.de")
        assertThat(user.firstname).isEqualTo("Alicia")
        assertThat(user.profileIcon).isEqualTo("icon-star")
        assertThat(user.workingArea).isEqualTo(WorkingArea.FRONTEND_DEV)
        assertThat(result.email).isEqualTo("new@mail.de")
        verify(exactly = 1) {
            keycloakAdminClient.updateUserProfile("auth-1", "new@mail.de", "Alicia", null)
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                UserWorkingAreaUpdatedEvent(user.id, WorkingArea.BACKEND_DEV, WorkingArea.FRONTEND_DEV),
            )
        }
    }

    @Test
    fun `patchAdminUserById forwards profile and permission group to Keycloak`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        user.roles.add(Role.USER)
        val request = PatchUserRequest(
            email = "new@mail.de",
            workingArea = WorkingArea.FRONTEND_DEV,
            permissionGroup = Role.ADMIN,
        )
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every {
            keycloakAdminClient.updateUserProfile("auth-1", "new@mail.de", null, null)
        } just runs
        every { keycloakAdminClient.setPermissionGroup("auth-1", Role.ADMIN) } just runs
        every { userRepository.save(user) } returns user
        every { eventPublisher.publishEvent(any<UserWorkingAreaUpdatedEvent>()) } just runs

        val result = userService.patchAdminUserById(user.id, request)

        assertThat(result.permissionGroup).isEqualTo(Role.ADMIN)
        assertThat(result.workingArea).isEqualTo(WorkingArea.FRONTEND_DEV)
        assertThat(user.roles).containsExactly(Role.USER)
        verify(exactly = 1) { keycloakAdminClient.setPermissionGroup("auth-1", Role.ADMIN) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                UserWorkingAreaUpdatedEvent(user.id, WorkingArea.BACKEND_DEV, WorkingArea.FRONTEND_DEV),
            )
        }
    }

    @Test
    fun `patchAdminUserById returns requested permission group without mutating local roles directly`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        user.roles.addAll(setOf(Role.USER, Role.ADMIN))
        val request = PatchUserRequest(permissionGroup = Role.PM)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every {
            keycloakAdminClient.updateUserProfile("auth-1", null, null, null)
        } just runs
        every { keycloakAdminClient.setPermissionGroup("auth-1", Role.PM) } just runs
        every { userRepository.save(user) } returns user

        val result = userService.patchAdminUserById(user.id, request)

        assertThat(user.roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
        assertThat(result.permissionGroup).isEqualTo(Role.PM)
    }

    @Test
    fun `updateUserEnabledById forwards enabled status to Keycloak`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { keycloakAdminClient.setUserEnabled("auth-1", false) } just runs
        every { userRepository.save(user) } returns user

        val result = userService.updateUserEnabledById(user.id, UpdateUserEnabledRequest(enabled = false))

        assertThat(user.enabled).isFalse()
        assertThat(result.enabled).isFalse()
        verify(exactly = 1) { keycloakAdminClient.setUserEnabled("auth-1", false) }
    }

    @Test
    fun `deleteAdminUserById deletes in Keycloak before local projection`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findAuthIdById(user.id) } returns Optional.of("auth-1")
        every { keycloakAdminClient.deleteUser("auth-1") } just runs
        every { userRepository.deleteRolesByUserId(user.id) } returns 1
        every { userRepository.deleteProjectionById(user.id) } returns 1

        val result = userService.deleteAdminUserById(user.id)

        assertThat(result.deleted).isTrue()
        verify(exactly = 1) { keycloakAdminClient.deleteUser("auth-1") }
        verify(exactly = 1) { userRepository.deleteRolesByUserId(user.id) }
        verify(exactly = 1) { userRepository.deleteProjectionById(user.id) }
    }

    @Test
    fun `deleteAdminUserById ignores local projection already deleted by Keycloak event`() {
        val user = user(authId = "auth-1", username = "alice", workingArea = WorkingArea.BACKEND_DEV)
        every { userRepository.findAuthIdById(user.id) } returns Optional.of("auth-1")
        every { keycloakAdminClient.deleteUser("auth-1") } just runs
        every { userRepository.deleteRolesByUserId(user.id) } returns 0
        every { userRepository.deleteProjectionById(user.id) } returns 0

        val result = userService.deleteAdminUserById(user.id)

        assertThat(result.id).isEqualTo(user.id)
        assertThat(result.deleted).isTrue()
    }

    @Test
    fun `getUserById throws NOT_FOUND when missing`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { userService.getUserById(id) }

        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

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
}
