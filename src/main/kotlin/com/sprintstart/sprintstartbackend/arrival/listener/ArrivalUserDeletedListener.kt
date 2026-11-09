package com.sprintstart.sprintstartbackend.arrival.listener

import com.sprintstart.sprintstartbackend.arrival.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.arrival.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Erases a deleted hire's arrival progress and the attestations about them.
 *
 * Attestations this person *granted* stay. They are evidence about somebody else's work, and
 * removing them would silently lower a colleague's ledger because an unrelated account was closed.
 */
@Component
class ArrivalUserDeletedListener(
    private val arrivalStepStateRepository: ArrivalStepStateRepository,
    private val attestationRepository: AttestationRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        arrivalStepStateRepository.deleteAllByUserId(event.userId)
        attestationRepository.deleteAllByHireId(event.userId)
    }
}
