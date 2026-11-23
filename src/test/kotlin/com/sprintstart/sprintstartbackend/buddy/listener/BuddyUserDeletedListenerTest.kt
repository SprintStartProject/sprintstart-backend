package com.sprintstart.sprintstartbackend.buddy.listener

import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID

class BuddyUserDeletedListenerTest {
    private val buddySessionRepository: BuddySessionRepository = mockk(relaxed = true)
    private val buddyMessageRepository: BuddyMessageRepository = mockk(relaxed = true)
    private val listener = BuddyUserDeletedListener(buddySessionRepository, buddyMessageRepository)
    private val userId = UUID.randomUUID()

    /** A row pointing at a parent that is already gone is not removable through its owner. */
    @Test
    fun `deleting a user erases the messages of every session before the sessions`() {
        val first = BuddySession(userId = userId)
        val second = BuddySession(userId = userId)
        every { buddySessionRepository.findByUserId(userId) } returns listOf(first, second)

        listener.onUserDeleted(UserDeletedEvent(userId))

        verifyOrder {
            buddyMessageRepository.deleteAllBySessionId(first.id)
            buddyMessageRepository.deleteAllBySessionId(second.id)
            buddySessionRepository.deleteAllByUserId(userId)
        }
    }

    @Test
    fun `a user who never opened the buddy is erased without a session to erase`() {
        every { buddySessionRepository.findByUserId(userId) } returns emptyList()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verify(exactly = 0) { buddyMessageRepository.deleteAllBySessionId(any()) }
        verify { buddySessionRepository.deleteAllByUserId(userId) }
    }
}
