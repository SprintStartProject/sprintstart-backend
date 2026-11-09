package com.sprintstart.sprintstartbackend.assistant.listener

import com.sprintstart.sprintstartbackend.assistant.repository.BuddyActionProposalRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Erases the actions an assistant proposed to a deleted user, and what they decided about them.
 */
@Component
class ProposalUserDeletedListener(
    private val buddyActionProposalRepository: BuddyActionProposalRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        buddyActionProposalRepository.deleteAllByUserId(event.userId)
    }
}
