package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The task a hire is on: the starter-work task they claimed as their goal.
 *
 * Extracted for the same reason [OpenPullRequestReader] was: the board's current-task card and the
 * task packet both read it, and "which task is this person on" is not a question two callers
 * should be able to answer differently.
 *
 * Read-only, always, and only ever what the hire chose. Nothing hands a hire a task: their
 * onboarding is the path their PM's blueprint prescribes, and claiming work is something they do
 * alongside it.
 */
@Component
class CurrentTaskReader(
    private val userGoalRepository: UserGoalRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
) {
    /** The task [hireId] claimed on [projectId], or null when they have claimed none. */
    fun currentTaskFor(hireId: UUID, projectId: UUID): StarterWorkTaskProposal? =
        userGoalRepository
            .findByUserIdAndProjectId(hireId, projectId)
            ?.sourceProposalId
            ?.let { starterWorkTaskProposalRepository.findById(it).orElse(null) }
}
