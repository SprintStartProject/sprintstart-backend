package com.sprintstart.sprintstartbackend.arrival.listener

import com.sprintstart.sprintstartbackend.arrival.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.arrival.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ArrivalUserDeletedListenerTest {
    private val arrivalStepStateRepository: ArrivalStepStateRepository = mockk(relaxed = true)
    private val attestationRepository: AttestationRepository = mockk(relaxed = true)
    private val listener = ArrivalUserDeletedListener(arrivalStepStateRepository, attestationRepository)
    private val userId = UUID.randomUUID()

    @Test
    fun `deleting a user erases their arrival progress`() {
        listener.onUserDeleted(UserDeletedEvent(userId))

        verify { arrivalStepStateRepository.deleteAllByUserId(userId) }
    }

    /**
     * Evidence this person gave about somebody else's work is not theirs to take with them.
     * Deleting by attester would quietly lower a colleague's ledger.
     */
    @Test
    fun `only the attestations about the hire go, not the ones they granted`() {
        listener.onUserDeleted(UserDeletedEvent(userId))

        verify(exactly = 1) { attestationRepository.deleteAllByHireId(userId) }
        confirmVerified(attestationRepository)
    }
}
