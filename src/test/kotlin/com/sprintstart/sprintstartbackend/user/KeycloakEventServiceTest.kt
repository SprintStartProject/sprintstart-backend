package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.events.UserCreatedEvent
import com.sprintstart.sprintstartbackend.user.model.dto.KeycloakEventRequest
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.KeycloakEventService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class KeycloakEventServiceTest {
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val service = KeycloakEventService(userRepository, eventPublisher)

    @Test
    fun `realm role mapping event replaces local permission groups with event role snapshot`() {
        val user = User(
            authId = "auth-1",
            username = "alice",
            email = "alice@mail.de",
            firstname = "Alice",
            lastname = "Developer",
        )
        user.roles.addAll(setOf(Role.USER, Role.ADMIN))
        every { userRepository.findLockedByAuthId("auth-1") } returns Optional.of(user)

        service.handleEvent(
            KeycloakEventRequest(
                source = "keycloak",
                resourceType = "REALM_ROLE_MAPPING",
                eventType = "DELETE",
                realmId = "sprintstart",
                authId = "auth-1",
                username = null,
                email = null,
                firstName = null,
                lastName = null,
                realmRoles = setOf("user", "project-manager"),
            ),
        )

        assertThat(user.roles).containsExactlyInAnyOrder(Role.USER, Role.PM)
    }

    @Test
    fun `user update event synchronizes role snapshot idempotently`() {
        val user = User(
            authId = "auth-1",
            username = "alice",
            email = "alice@mail.de",
            firstname = "Alice",
            lastname = "Developer",
        )
        user.roles.addAll(setOf(Role.USER, Role.ADMIN))
        every { userRepository.findLockedByAuthId("auth-1") } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        service.handleEvent(
            KeycloakEventRequest(
                source = "keycloak",
                resourceType = "USER",
                eventType = "UPDATE",
                realmId = "sprintstart",
                authId = "auth-1",
                username = "alice2",
                email = null,
                firstName = null,
                lastName = null,
                realmRoles = setOf("user", "admin"),
            ),
        )

        assertThat(user.username).isEqualTo("alice2")
        assertThat(user.roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
    }

    @Test
    fun `user update event synchronizes enabled account state`() {
        val user = User(
            authId = "auth-1",
            username = "alice",
            email = "alice@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            enabled = true,
        )
        every { userRepository.findLockedByAuthId("auth-1") } returns Optional.of(user)
        every { userRepository.save(user) } returns user

        service.handleEvent(
            KeycloakEventRequest(
                source = "keycloak",
                resourceType = "USER",
                eventType = "UPDATE",
                realmId = "sprintstart",
                authId = "auth-1",
                username = null,
                email = null,
                firstName = null,
                lastName = null,
                enabled = false,
                realmRoles = emptySet(),
            ),
        )

        assertThat(user.enabled).isFalse()
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `user delete event is ignored when local projection is already gone`() {
        every { userRepository.findLockedByAuthId("auth-1") } returns Optional.empty()

        service.handleEvent(
            KeycloakEventRequest(
                source = "keycloak",
                resourceType = "USER",
                eventType = "DELETE",
                realmId = "sprintstart",
                authId = "auth-1",
                username = null,
                email = null,
                firstName = null,
                lastName = null,
                realmRoles = emptySet(),
            ),
        )

        verify(exactly = 0) { userRepository.delete(any<User>()) }
    }

    @Test
    fun `register user event publishes UserCreatedEvent with the saved user id`() {
        val savedUser = slot<User>()
        every { userRepository.save(capture(savedUser)) } answers { savedUser.captured }

        service.handleEvent(
            KeycloakEventRequest(
                source = "keycloak",
                resourceType = "USER",
                eventType = "REGISTER",
                realmId = "sprintstart",
                authId = "auth-new",
                username = "newuser",
                email = "new@example.com",
                firstName = "New",
                lastName = "User",
                realmRoles = setOf("user"),
            ),
        )

        val publishedEvent = slot<UserCreatedEvent>()
        verify(exactly = 1) { eventPublisher.publishEvent(capture(publishedEvent)) }
        assertThat(publishedEvent.captured.userId).isEqualTo(savedUser.captured.id)
    }
}
