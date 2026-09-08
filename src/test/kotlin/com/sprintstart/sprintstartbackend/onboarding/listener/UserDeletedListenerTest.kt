package com.sprintstart.sprintstartbackend.onboarding.listener

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.AutonomyMilestoneRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.GithubHistoryPriorRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID

class UserDeletedListenerTest {
    private val buddySessionRepository: BuddySessionRepository = mockk(relaxed = true)
    private val buddyMessageRepository: BuddyMessageRepository = mockk(relaxed = true)
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk(relaxed = true)
    private val arrivalStepStateRepository: ArrivalStepStateRepository = mockk(relaxed = true)
    private val boardRepository: BoardRepository = mockk(relaxed = true)
    private val boardCardRepository: BoardCardRepository = mockk(relaxed = true)
    private val userGoalRepository: UserGoalRepository = mockk(relaxed = true)
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository = mockk(relaxed = true)
    private val autonomyMilestoneRepository: AutonomyMilestoneRepository = mockk(relaxed = true)
    private val attestationRepository: AttestationRepository = mockk(relaxed = true)
    private val knowledgeRequestRepository: KnowledgeRequestRepository = mockk(relaxed = true)
    private val githubHistoryPriorRepository: GithubHistoryPriorRepository = mockk(relaxed = true)

    private val listener = UserDeletedListener(
        buddySessionRepository,
        buddyMessageRepository,
        userCompetencyStateRepository,
        arrivalStepStateRepository,
        boardRepository,
        boardCardRepository,
        userGoalRepository,
        taskZeroAssignmentRepository,
        autonomyMilestoneRepository,
        attestationRepository,
        knowledgeRequestRepository,
        githubHistoryPriorRepository,
    )

    private val userId = UUID.randomUUID()

    private fun hasConversationAndBoard(): Board {
        val session = BuddySession(userId = userId)
        val board = Board(userId = userId, projectId = UUID.randomUUID())
        every { buddySessionRepository.findByUserId(userId) } returns session
        every { boardRepository.findAllByUserId(userId) } returns listOf(board)
        return board
    }

    /**
     * The conversation is the most of this person the system holds: every message they sent, and
     * the running note the model keeps about them. Nothing else reaches it once the account is
     * gone.
     */
    @Test
    fun `deleting a user erases their buddy conversation and the note about them`() {
        hasConversationAndBoard()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verify { buddyMessageRepository.deleteAllBySessionId(any()) }
        verify { buddySessionRepository.deleteAllByUserId(userId) }
    }

    @Test
    fun `deleting a user erases everything onboarding recorded about them`() {
        hasConversationAndBoard()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verify { userCompetencyStateRepository.deleteAllByUserId(userId) }
        verify { arrivalStepStateRepository.deleteAllByUserId(userId) }
        verify { userGoalRepository.deleteAllByUserId(userId) }
        verify { taskZeroAssignmentRepository.deleteAllByHireId(userId) }
        verify { autonomyMilestoneRepository.deleteAllByHireId(userId) }
        verify { attestationRepository.deleteAllByHireId(userId) }
        verify { knowledgeRequestRepository.deleteAllByHireId(userId) }
        verify { githubHistoryPriorRepository.deleteAllByUserId(userId) }
    }

    /** A row pointing at a parent that is already gone is not removable through its owner. */
    @Test
    fun `children go before the rows that own them`() {
        val board = hasConversationAndBoard()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verifyOrder {
            buddyMessageRepository.deleteAllBySessionId(any())
            buddySessionRepository.deleteAllByUserId(userId)
        }
        verifyOrder {
            boardCardRepository.deleteAllByBoardId(board.id)
            boardRepository.deleteAllByUserId(userId)
        }
    }

    /**
     * Evidence this person gave about somebody else's work is not theirs to take with them.
     * Deleting by attester would quietly lower a colleague's ledger because an unrelated account
     * was closed.
     */
    @Test
    fun `attestations this person granted to others are left alone`() {
        hasConversationAndBoard()

        listener.onUserDeleted(UserDeletedEvent(userId))

        // The only thing asked of the attestations is to drop the ones about this hire. Anything
        // else reaching this repository would be taking evidence off somebody else's record.
        verify(exactly = 1) { attestationRepository.deleteAllByHireId(userId) }
        confirmVerified(attestationRepository)
    }

    @Test
    fun `a user who never opened the buddy is erased without a session to erase`() {
        every { buddySessionRepository.findByUserId(userId) } returns null
        every { boardRepository.findAllByUserId(userId) } returns emptyList()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verify(exactly = 0) { buddyMessageRepository.deleteAllBySessionId(any()) }
        verify { userCompetencyStateRepository.deleteAllByUserId(userId) }
    }
}
