package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.ConversationCompaction
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Component

/**
 * Folds a hire's conversation backlog into the buddy's memory in the background. Nobody waits on the fold;
 * one that fails costs a longer prompt on the next turn and nothing else.
 */
@Component
class BuddyCompactionScheduler(
    private val hireCompaction: BuddyCompactionService,
    private val compaction: ConversationCompaction,
) {
    /** Folds a hire's conversation once the turn that grew it is stored. */
    fun foldHireConversation(user: UserId, session: BuddySessionId) {
        compaction.inBackground { hireCompaction.compactIfNeeded(user.value, session.value) }
    }
}
