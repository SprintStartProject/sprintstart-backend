package com.sprintstart.sprintstartbackend.teamspace.listener

import com.sprintstart.sprintstartbackend.teamspace.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.teamspace.repository.BuddyTeamMessageRepository
import com.sprintstart.sprintstartbackend.teamspace.repository.BuddyTeamSessionRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID

class TeamUserDeletedListenerTest {
    private val buddyTeamSessionRepository: BuddyTeamSessionRepository = mockk(relaxed = true)
    private val buddyTeamMessageRepository: BuddyTeamMessageRepository = mockk(relaxed = true)
    private val listener = TeamUserDeletedListener(buddyTeamSessionRepository, buddyTeamMessageRepository)
    private val userId = UUID.randomUUID()

    @Test
    fun `deleting a user erases their team conversations on every project, messages first`() {
        val alpha = BuddyTeamSession(userId = userId, projectId = UUID.randomUUID())
        val beta = BuddyTeamSession(userId = userId, projectId = UUID.randomUUID())
        every { buddyTeamSessionRepository.findAllByUserId(userId) } returns listOf(alpha, beta)

        listener.onUserDeleted(UserDeletedEvent(userId))

        verifyOrder {
            buddyTeamMessageRepository.deleteAllBySessionId(alpha.id)
            buddyTeamMessageRepository.deleteAllBySessionId(beta.id)
            buddyTeamSessionRepository.deleteAllByUserId(userId)
        }
    }

    @Test
    fun `a user who never used team mode is erased without a session to erase`() {
        every { buddyTeamSessionRepository.findAllByUserId(userId) } returns emptyList()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verify(exactly = 0) { buddyTeamMessageRepository.deleteAllBySessionId(any()) }
        verify { buddyTeamSessionRepository.deleteAllByUserId(userId) }
    }
}
