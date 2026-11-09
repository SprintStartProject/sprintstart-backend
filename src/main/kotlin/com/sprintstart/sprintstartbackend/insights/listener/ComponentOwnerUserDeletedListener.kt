package com.sprintstart.sprintstartbackend.insights.listener

import com.sprintstart.sprintstartbackend.insights.repository.ComponentOwnerRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Removes a deleted user as owner of their components.
 *
 * Otherwise knowledge gaps on those components would keep naming an owner nobody can reach. The
 * components themselves and their other owners stay.
 */
@Component
class ComponentOwnerUserDeletedListener(
    private val componentOwnerRepository: ComponentOwnerRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        componentOwnerRepository.deleteAllByUserId(event.userId)
    }
}
