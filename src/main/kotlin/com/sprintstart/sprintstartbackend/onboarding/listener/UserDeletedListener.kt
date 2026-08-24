package com.sprintstart.sprintstartbackend.onboarding.listener

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
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Erases what onboarding recorded about a user when their account is deleted.
 *
 * Onboarding holds more about a person than any other module: every conversation they had with the
 * buddy, the running note the model keeps about them, the level they were placed at, what they were
 * asked to set up, and what they were working toward. None of it is reachable once the account is
 * gone, so leaving it behind keeps a description of somebody who is no longer here.
 *
 * Runs in the deleting transaction, so a failed deletion takes the erasure with it and the two
 * cannot end up disagreeing.
 *
 * ### What is deliberately kept
 *
 * Attestations this person *granted* stay. They are evidence about somebody else's work, and
 * removing them would silently lower a colleague's ledger because an unrelated account was closed.
 * Only the ones about the deleted user go.
 *
 * The competency catalogue, the starter-work pool and canonical answers are the team's, not the
 * user's, and are untouched.
 */
@Component
class UserDeletedListener(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val arrivalStepStateRepository: ArrivalStepStateRepository,
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val userGoalRepository: UserGoalRepository,
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository,
    private val autonomyMilestoneRepository: AutonomyMilestoneRepository,
    private val attestationRepository: AttestationRepository,
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val githubHistoryPriorRepository: GithubHistoryPriorRepository,
) {
    @EventListener
    @Transactional
    fun onUserDeleted(event: UserDeletedEvent) {
        val userId = event.userId

        // Children before parents: a buddy message points at its session, and a card at its board,
        // so removing the owner first would leave a row nothing can reach.
        eraseConversations(userId)
        eraseBoards(userId)

        userCompetencyStateRepository.deleteAllByUserId(userId)
        arrivalStepStateRepository.deleteAllByUserId(userId)
        userGoalRepository.deleteAllByUserId(userId)
        taskZeroAssignmentRepository.deleteAllByHireId(userId)
        autonomyMilestoneRepository.deleteAllByHireId(userId)
        attestationRepository.deleteAllByHireId(userId)
        knowledgeRequestRepository.deleteAllByHireId(userId)
        githubHistoryPriorRepository.deleteAllByUserId(userId)
    }

    private fun eraseConversations(userId: UUID) {
        buddySessionRepository.findByUserId(userId)?.let {
            buddyMessageRepository.deleteAllBySessionId(it.id)
        }
        buddySessionRepository.deleteAllByUserId(userId)
    }

    private fun eraseBoards(userId: UUID) {
        boardRepository.findAllByUserId(userId).forEach {
            boardCardRepository.deleteAllByBoardId(it.id)
        }
        boardRepository.deleteAllByUserId(userId)
    }
}
