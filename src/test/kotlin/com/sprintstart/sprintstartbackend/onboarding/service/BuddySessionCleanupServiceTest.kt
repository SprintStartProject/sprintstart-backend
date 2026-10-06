package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionStatus
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class BuddySessionCleanupServiceTest {
    private val sessionRepository: BuddySessionRepository = mockk()
    private val messageRepository: BuddyMessageRepository = mockk()
    private val citationRepository: BuddyCitationRepository = mockk()
    private val clock: Clock = mockk()

    private lateinit var service: BuddySessionCleanupService

    private val now = Instant.parse("2026-10-05T12:00:00Z")

    @BeforeEach
    fun setUp() {
        service = BuddySessionCleanupService(
            sessionRepository = sessionRepository,
            messageRepository = messageRepository,
            citationRepository = citationRepository,
            clock = clock,
        )

        every { clock.instant() } returns now
        every {
            citationRepository.deleteAllByMessageSessionId(any())
        } just runs
        every {
            messageRepository.deleteAllBySessionId(any())
        } just runs
        every {
            sessionRepository.delete(any())
        } just runs
    }

    @Test
    fun `deletes binned chats older than seven days`() {
        val session1 = mockk<BuddySession>()
        val session2 = mockk<BuddySession>()

        val sessionId1 = UUID.randomUUID()
        val sessionId2 = UUID.randomUUID()

        every { session1.id } returns sessionId1
        every { session2.id } returns sessionId2

        val cutoff = now.minus(7, ChronoUnit.DAYS)

        every {
            sessionRepository.findByStatusAndBinnedAtBefore(
                BuddySessionStatus.BINNED,
                cutoff,
            )
        } returns listOf(session1, session2)

        service.deleteBinnedChats()

        verify {
            sessionRepository.findByStatusAndBinnedAtBefore(
                BuddySessionStatus.BINNED,
                cutoff,
            )
        }

        verify {
            citationRepository.deleteAllByMessageSessionId(sessionId1)
            citationRepository.deleteAllByMessageSessionId(sessionId2)
        }

        verify {
            messageRepository.deleteAllBySessionId(sessionId1)
            messageRepository.deleteAllBySessionId(sessionId2)
        }

        verify {
            sessionRepository.delete(session1)
            sessionRepository.delete(session2)
        }
    }

    @Test
    fun `does nothing when there are no sessions to delete`() {
        val cutoff = now.minus(7, ChronoUnit.DAYS)

        every {
            sessionRepository.findByStatusAndBinnedAtBefore(
                BuddySessionStatus.BINNED,
                cutoff,
            )
        } returns emptyList()

        service.deleteBinnedChats()

        verify(exactly = 1) {
            sessionRepository.findByStatusAndBinnedAtBefore(
                BuddySessionStatus.BINNED,
                cutoff,
            )
        }

        verify(exactly = 0) {
            citationRepository.deleteAllByMessageSessionId(any())
        }
        verify(exactly = 0) {
            messageRepository.deleteAllBySessionId(any())
        }
        verify(exactly = 0) {
            sessionRepository.delete(any())
        }
    }
}
