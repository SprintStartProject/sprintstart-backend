package com.sprintstart.sprintstartbackend.buddy.listener

import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Erases a deleted user's conversations with their buddy.
 *
 * The conversation is the most of a person the system holds: every message they sent, and the
 * running note the model keeps about them. Messages go before the sessions they belong to, so no row
 * is left that nothing can reach.
 */
@Component
class BuddyUserDeletedListener(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        buddySessionRepository.findByUserId(event.userId).forEach {
            buddyMessageRepository.deleteAllBySessionId(it.id)
        }
        buddySessionRepository.deleteAllByUserId(event.userId)
    }
}
