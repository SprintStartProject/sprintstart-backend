package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactResponse
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.Optional
import java.util.UUID

/**
 * A manager's team conversation is folded by the same rules as the hire's own
 * ([BuddyCompactionServiceTest] owns those), from its own tables and never into the hire's note.
 */
class BuddyTeamCompactionTest {
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val buddyMessageRepository: BuddyMessageRepository = mockk()
    private val buddyTeamSessionRepository: BuddyTeamSessionRepository = mockk()
    private val buddyTeamMessageRepository: BuddyTeamMessageRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()

    private val transactionManager: PlatformTransactionManager = mockk {
        every { getTransaction(any()) } returns SimpleTransactionStatus()
        every { commit(any<TransactionStatus>()) } returns Unit
        every { rollback(any<TransactionStatus>()) } returns Unit
    }

    private val service = BuddyCompactionService(
        buddySessionRepository,
        buddyMessageRepository,
        buddyTeamSessionRepository,
        buddyTeamMessageRepository,
        onboardingAiClient,
        transactionManager,
    )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    private fun teamSessionWith(messageCount: Int, cursor: Int = 0): BuddyTeamSession {
        val session = BuddyTeamSession(userId = userId, projectId = projectId, summarizedCount = cursor)
        every { buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId) } returns session
        every { buddyTeamSessionRepository.findById(session.id) } returns Optional.of(session)
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns
            (1..messageCount).map {
                BuddyTeamMessage(
                    session = session,
                    role = if (it % 2 == 1) BuddyMessageRole.USER else BuddyMessageRole.ASSISTANT,
                    content = "t$it",
                )
            }
        return session
    }

    @Test
    fun `folds the team conversation's oldest messages into its own note`() = runTest {
        val session = teamSessionWith(messageCount = 24)
        every { buddyTeamSessionRepository.save(any()) } answers { firstArg() }
        val requests = mutableListOf<BuddyCompactRequest>()
        coEvery { onboardingAiClient.compactBuddyMemory(capture(requests)) } returns
            BuddyCompactResponse(memory = "Asked about Sam twice.")

        service.compactTeamIfNeeded(userId, projectId)

        assertThat(requests.single().folded.map { it.content }).containsExactly("t1", "t2", "t3", "t4")
        assertThat(session.summary).isEqualTo("Asked about Sam twice.")
        assertThat(session.summarizedCount).isEqualTo(4)
    }

    /** The hire's note is shown on their board; team talk must never be folded into it. */
    @Test
    fun `never reads or writes the manager's own onboarding session`() = runTest {
        teamSessionWith(messageCount = 24)
        every { buddyTeamSessionRepository.save(any()) } answers { firstArg() }
        coEvery { onboardingAiClient.compactBuddyMemory(any()) } returns BuddyCompactResponse(memory = "note")

        service.compactTeamIfNeeded(userId, projectId)

        verify(exactly = 0) { buddySessionRepository.findByUserId(any()) }
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    @Test
    fun `does nothing while the team window still fits`() = runTest {
        teamSessionWith(messageCount = 20)

        service.compactTeamIfNeeded(userId, projectId)

        coVerify(exactly = 0) { onboardingAiClient.compactBuddyMemory(any()) }
    }

    @Test
    fun `discards its fold when the team cursor moved while the model was thinking`() = runTest {
        val session = teamSessionWith(messageCount = 24)
        coEvery { onboardingAiClient.compactBuddyMemory(any()) } answers {
            session.summarizedCount = 4
            session.summary = "somebody else's note"
            BuddyCompactResponse(memory = "my note")
        }

        service.compactTeamIfNeeded(userId, projectId)

        assertThat(session.summary).isEqualTo("somebody else's note")
        verify(exactly = 0) { buddyTeamSessionRepository.save(any()) }
    }

    @Test
    fun `does nothing when the manager has no team conversation on the project`() = runTest {
        every { buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId) } returns null

        service.compactTeamIfNeeded(userId, projectId)

        coVerify(exactly = 0) { onboardingAiClient.compactBuddyMemory(any()) }
    }
}
