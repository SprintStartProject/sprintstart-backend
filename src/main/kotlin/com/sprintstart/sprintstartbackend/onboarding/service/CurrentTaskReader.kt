package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The task a hire is on: their claimed goal, or their assigned Task 0 before they have one.
 *
 * Extracted for the same reason [OpenPullRequestReader] was: the ramp reads it and the board's
 * current-task card reads it, and "which task is this person on" is not a question two callers
 * should be able to answer differently. A hire told one thing on their board and another on their
 * ramp has no way to know which is true.
 *
 * **Read-only, always.** Unlike `TaskZeroService.getForHire`, this never assigns. Seeing where you
 * are must never be what hands you your first task — which matters more here than it did in the
 * ramp, because a board card is hydrated on every page load.
 */
@Component
class CurrentTaskReader(
    private val userGoalRepository: UserGoalRepository,
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
) {
    /**
     * The task [hireId] is on for [projectId], or null when they are on none.
     *
     * A claimed goal outranks an assigned Task 0: Task 0 is what somebody is handed, a goal is what
     * they chose, and once they have chosen the choice is the answer.
     */
    fun currentTaskFor(hireId: UUID, projectId: UUID): StarterWorkTaskProposal? =
        userGoalRepository
            .findByUserIdAndProjectId(hireId, projectId)
            ?.sourceProposalId
            ?.let { starterWorkTaskProposalRepository.findById(it).orElse(null) }
            ?: taskZeroAssignmentRepository
                .findByHireIdAndProjectId(hireId, projectId)
                ?.let { starterWorkTaskProposalRepository.findById(it.proposalId).orElse(null) }

    /** Whether the current task was chosen by the hire (a claimed goal) rather than handed to them. */
    fun isClaimedGoal(hireId: UUID, projectId: UUID): Boolean =
        userGoalRepository.findByUserIdAndProjectId(hireId, projectId)?.sourceProposalId != null
}
