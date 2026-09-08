package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.events.UserSessionStartedEvent
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.SessionActivityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

class SessionActivityServiceTest {
    private val userRepository: UserRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val idleThreshold = Duration.ofHours(4)
    private val service = SessionActivityService(userRepository, eventPublisher, idleThreshold)

    private val authId = "auth|test-user"

    private fun user(lastSeenAt: Instant?) = User(
        id = UUID.randomUUID(),
        authId = authId,
        username = "test",
        email = null,
        firstname = "Test",
        lastname = "User",
        lastSeenAt = lastSeenAt,
    )

    @Test
    fun `does nothing when no user exists for the auth id`() {
        every { userRepository.findByAuthId(authId) } returns Optional.empty()

        service.recordActivity(authId)

        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `stamps lastSeenAt but does not publish on the first-ever activity`() {
        val user = user(lastSeenAt = null)
        every { userRepository.findByAuthId(authId) } returns Optional.of(user)

        service.recordActivity(authId)

        assertThat(user.lastSeenAt).isNotNull
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `does not publish when the gap since last activity is under the threshold`() {
        val user = user(lastSeenAt = Instant.now().minus(Duration.ofMinutes(30)))
        every { userRepository.findByAuthId(authId) } returns Optional.of(user)

        service.recordActivity(authId)

        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `publishes a session-started event when the idle gap exceeds the threshold`() {
        val user = user(lastSeenAt = Instant.now().minus(Duration.ofHours(5)))
        every { userRepository.findByAuthId(authId) } returns Optional.of(user)

        service.recordActivity(authId)

        verify(exactly = 1) { eventPublisher.publishEvent(UserSessionStartedEvent(user.id)) }
    }
}
