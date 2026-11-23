package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.CompactionStore
import com.sprintstart.sprintstartbackend.assistant.external.ConversationCompaction
import com.sprintstart.sprintstartbackend.buddy.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import org.springframework.stereotype.Service
import java.util.UUID

/** Folds a hire's own conversation by the kernel's [ConversationCompaction] rules, from the Buddy's tables. */
@Service
class BuddyCompactionService(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val compaction: ConversationCompaction,
) {
    /**
     * Folds this hire's backlog into their memory note, if it has one.
     *
     * A no-op while the window fits, and never throws, as [ConversationCompaction.compact] promises.
     *
     * @param userId The hire who owns the session.
     * @param sessionId The session to compact.
     */
    suspend fun compactIfNeeded(userId: UUID, sessionId: UUID) {
        compaction.compact(
            CompactionStore(
                label = "user $userId",
                find = { buddySessionRepository.findByIdAndUserId(sessionId, userId) },
                findById = { buddySessionRepository.findById(it).orElse(null) },
                transcript = { id ->
                    buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(id).map { it.toAgentMessage() }
                },
                save = { buddySessionRepository.save(it) },
            ),
        )
    }
}
