package com.sprintstart.sprintstartbackend.starterwork.listener

import com.sprintstart.sprintstartbackend.starterwork.repository.GithubHistoryPriorRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Erases what starter-work matching learned about a deleted user: the level they were placed at,
 * what they were working toward, and the prior read from their GitHub history.
 *
 * The competency catalogue and the starter-work pool are the team's, not the user's, and stay.
 */
@Component
class StarterWorkUserDeletedListener(
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val userGoalRepository: UserGoalRepository,
    private val githubHistoryPriorRepository: GithubHistoryPriorRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        userCompetencyStateRepository.deleteAllByUserId(event.userId)
        userGoalRepository.deleteAllByUserId(event.userId)
        githubHistoryPriorRepository.deleteAllByUserId(event.userId)
    }
}
