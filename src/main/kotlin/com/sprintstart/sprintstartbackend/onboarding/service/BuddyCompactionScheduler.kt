package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

/**
 * Folds a conversation's backlog into the buddy's memory in the background. Nobody waits on the fold; one
 * that fails costs a longer prompt on the next turn and nothing else.
 */
@Component
class BuddyCompactionScheduler(
    private val compaction: BuddyCompactionService,
    private val applicationScope: CoroutineScope,
) {
    /** Folds a hire's conversation once the turn that grew it is stored. */
    fun foldHireConversation(user: UserId, session: BuddySessionId) {
        applicationScope.launch { compaction.compactIfNeeded(user.value, session.value) }
    }

    /** Folds a manager's team conversation about a project. */
    fun foldTeamConversation(user: UserId, project: ProjectId) {
        applicationScope.launch { compaction.compactTeamIfNeeded(user.value, project.value) }
    }
}
