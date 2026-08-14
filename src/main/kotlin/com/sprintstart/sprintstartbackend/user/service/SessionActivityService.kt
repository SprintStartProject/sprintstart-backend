package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.events.UserSessionStartedEvent
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Detects session boundaries from authenticated request traffic.
 *
 * This backend is a stateless JWT resource server with no real login/session concept, and the
 * Keycloak event listener SPI filters `LOGIN` events out before they would ever reach this
 * backend (confirmed by inspecting the vendored `event-spi` jar; its source lives outside this
 * repo). [recordActivity] is the pragmatic stand-in: it stamps
 * [User.lastSeenAt][com.sprintstart.sprintstartbackend.user.model.entity.User.lastSeenAt] on every
 * call, and publishes [UserSessionStartedEvent] when the gap since the previous stamp exceeds
 * [idleThreshold] -- treating that request as the start of a new session.
 */
@Service
class SessionActivityService(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${sprintstart.session.idle-threshold}")
    private val idleThreshold: Duration,
) {
    /**
     * Records authenticated activity for the user identified by [authId], publishing
     * [UserSessionStartedEvent] when the gap since their last recorded activity exceeds
     * [idleThreshold].
     *
     * A user's very first-ever recorded activity never publishes -- there is no prior baseline to
     * reconcile against, and [CompetencyPathService]
     * [com.sprintstart.sprintstartbackend.onboarding.service.CompetencyPathService] lazily
     * creates a hire's graph pin on their first path request regardless.
     *
     * @param authId External authentication identifier from the JWT subject.
     */
    @Transactional
    fun recordActivity(authId: String) {
        val user = userRepository.findByAuthId(authId).orElse(null) ?: return

        val previous = user.lastSeenAt
        val now = Instant.now()
        user.lastSeenAt = now

        if (previous != null && Duration.between(previous, now) > idleThreshold) {
            eventPublisher.publishEvent(UserSessionStartedEvent(user.id))
        }
    }
}
