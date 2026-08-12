package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Folds a buddy conversation's oldest turns into the mentor's durable memory note.
 *
 * ⚠️ **Must stay off the answering path.** Folding during an agent turn runs before the model
 * composes its reply, and since the cursor advances by exactly what it folds, the active window
 * then sits at [BuddyService.WINDOW] permanently — an extra serialized model call on every turn.
 *
 * The model call happens outside any transaction, so the write re-reads what it enforces. Two
 * guards catch different races:
 *
 * - **The cursor comparison** catches a fold that committed while the model was thinking.
 * - **[com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession.version]** catches
 *   one committing *between* the re-read and the flush. ⚠️ A re-check alone is not a lock.
 *
 * Losing either race discards this fold and leaves the cursor alone; the next turn retries.
 */
@Service
class BuddyCompactionService(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val onboardingAiClient: OnboardingAiClient,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTxTemplate = TransactionTemplate(transactionManager)

    /**
     * Folds this session's backlog into its memory note, if it has one.
     *
     * Safe to call after every turn: a conversation whose active window still fits does nothing and
     * costs one query. ⚠️ **Never throws** — the caller is a fire-and-forget launch.
     *
     * @param userId The hire whose session to compact.
     */
    suspend fun compactIfNeeded(userId: UUID) {
        val plan = readTxTemplate.execute { planFor(userId) } ?: return

        val request = BuddyCompactRequest(priorSummary = plan.priorSummary, folded = plan.folded)
        val memory = try {
            onboardingAiClient.compactBuddyMemory(request).memory
        } catch (@Suppress("SwallowedException") e: OnboardingAiException) {
            // The note shapes the prompt and is not the record, so a failed fold costs only a
            // longer prompt next turn. Warned, not retried.
            logger.warn("Buddy compaction skipped for user {}: {}", userId, e.message)
            return
        }

        applyFold(plan, memory)
    }

    private fun planFor(userId: UUID): FoldPlan? {
        val session = buddySessionRepository.findByUserId(userId) ?: return null
        val messages = buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        // Folds whatever it takes to bring the active window back to WINDOW, not a fixed slice, so
        // a pass that missed its turn catches up in one call.
        val foldCount = messages.size - session.summarizedCount - BuddyService.WINDOW
        if (foldCount <= 0) return null
        return FoldPlan(
            sessionId = session.id,
            cursor = session.summarizedCount,
            priorSummary = session.summary,
            folded = messages
                .drop(session.summarizedCount)
                .take(foldCount)
                .map { it.toAgentMessage() },
        )
    }

    private fun applyFold(plan: FoldPlan, memory: String) {
        try {
            writeTxTemplate.execute {
                val session = buddySessionRepository.findById(plan.sessionId).orElse(null)
                    ?: return@execute
                if (session.summarizedCount != plan.cursor) {
                    // A fold committed while the model was thinking. Applying this one would move
                    // the cursor past messages the other pass's note does not cover.
                    logger.debug(
                        "Discarding stale buddy fold for session {}: cursor moved {} -> {}",
                        plan.sessionId,
                        plan.cursor,
                        session.summarizedCount,
                    )
                    return@execute
                }
                session.summary = memory
                session.summarizedCount = plan.cursor + plan.folded.size
                buddySessionRepository.save(session)
            }
        } catch (@Suppress("SwallowedException") e: ObjectOptimisticLockingFailureException) {
            // A concurrent fold committed between the re-read and the flush. Same outcome as a
            // moved cursor: discard, and the next turn retries.
            logger.debug("Buddy fold for session {} lost the swap: {}", plan.sessionId, e.message)
        }
    }

    /**
     * One fold, decided under a read transaction and applied under a write one.
     *
     * [cursor] is the value the write must still see, not the value it will set.
     */
    private data class FoldPlan(
        val sessionId: UUID,
        val cursor: Int,
        val priorSummary: String?,
        val folded: List<BuddyAgentMessageDto>,
    )
}
