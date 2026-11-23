package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.AgentAiClient
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCompactResponse
import com.sprintstart.sprintstartbackend.assistant.external.ConversationCompaction
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.teamspace.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.teamspace.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.teamspace.repository.BuddyTeamMessageRepository
import com.sprintstart.sprintstartbackend.teamspace.repository.BuddyTeamSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val agentAiClient: AgentAiClient = mockk()

    private val transactionManager: PlatformTransactionManager = mockk {
        every { getTransaction(any()) } returns SimpleTransactionStatus()
        every { commit(any<TransactionStatus>()) } returns Unit
        every { rollback(any<TransactionStatus>()) } returns Unit
    }

    private val compaction =
        ConversationCompaction(agentAiClient, CoroutineScope(Dispatchers.Unconfined), transactionManager)

    // Unconfined runs the background fold inline, so a test sees its outcome as soon as it is scheduled.
    private val teamCompaction = BuddyTeamCompaction(
        compaction,
        buddyTeamSessionRepository,
        buddyTeamMessageRepository,
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
        coEvery { agentAiClient.compactBuddyMemory(capture(requests)) } returns
            BuddyCompactResponse(memory = "Asked about Sam twice.")

        teamCompaction.compactIfNeeded(UserId(userId), ProjectId(projectId))

        assertThat(requests.single().folded.map { it.content }).containsExactly("t1", "t2", "t3", "t4")
        assertThat(session.summary).isEqualTo("Asked about Sam twice.")
        assertThat(session.summarizedCount).isEqualTo(4)
    }

    @Test
    fun `a fold scheduled after a turn runs in the background and folds the team conversation`() {
        val session = teamSessionWith(messageCount = 24)
        every { buddyTeamSessionRepository.save(any()) } answers { firstArg() }
        coEvery { agentAiClient.compactBuddyMemory(any()) } returns BuddyCompactResponse(memory = "Asked about Sam.")

        teamCompaction.foldInBackground(UserId(userId), ProjectId(projectId))

        assertThat(session.summary).isEqualTo("Asked about Sam.")
        assertThat(session.summarizedCount).isEqualTo(4)
    }

    /** The hire's note is shown on their board; team talk must never be folded into it. */
    @Test
    fun `never reads or writes the manager's own onboarding session`() = runTest {
        teamSessionWith(messageCount = 24)
        every { buddyTeamSessionRepository.save(any()) } answers { firstArg() }
        coEvery { agentAiClient.compactBuddyMemory(any()) } returns BuddyCompactResponse(memory = "note")

        teamCompaction.compactIfNeeded(UserId(userId), ProjectId(projectId))

        verify(exactly = 0) { buddySessionRepository.findByUserId(any()) }
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    @Test
    fun `does nothing while the team window still fits`() = runTest {
        teamSessionWith(messageCount = 20)

        teamCompaction.compactIfNeeded(UserId(userId), ProjectId(projectId))

        coVerify(exactly = 0) { agentAiClient.compactBuddyMemory(any()) }
    }

    @Test
    fun `discards its fold when the team cursor moved while the model was thinking`() = runTest {
        val session = teamSessionWith(messageCount = 24)
        coEvery { agentAiClient.compactBuddyMemory(any()) } answers {
            session.summarizedCount = 4
            session.summary = "somebody else's note"
            BuddyCompactResponse(memory = "my note")
        }

        teamCompaction.compactIfNeeded(UserId(userId), ProjectId(projectId))

        assertThat(session.summary).isEqualTo("somebody else's note")
        verify(exactly = 0) { buddyTeamSessionRepository.save(any()) }
    }

    @Test
    fun `does nothing when the manager has no team conversation on the project`() = runTest {
        every { buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId) } returns null

        teamCompaction.compactIfNeeded(UserId(userId), ProjectId(projectId))

        coVerify(exactly = 0) { agentAiClient.compactBuddyMemory(any()) }
    }
}
