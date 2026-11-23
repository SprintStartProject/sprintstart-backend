package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Folds a conversation's oldest turns into the assistant's durable memory note.
 *
 * Must stay off the answering path. Folding during an agent turn runs before the model
 * composes its reply, and since the cursor advances by exactly what it folds, the active window
 * then sits at [CONVERSATION_WINDOW] permanently — an extra serialized model call on every turn.
 *
 * The model call happens outside any transaction, so the write re-reads what it enforces. Two
 * guards catch different races:
 *
 * - The cursor comparison catches a fold that committed while the model was thinking.
 * - The session's `@Version` catches one committing *between* the re-read and the flush. A re-check
 *   alone is not a lock.
 *
 * Losing either race discards this fold and leaves the cursor alone; the next turn retries.
 *
 * The hire's own conversation and a manager's team conversation are folded by exactly these rules.
 * They differ only in where the session and its transcript are stored, which is all [CompactionStore]
 * captures; each assistant builds its own.
 */
@Service
class ConversationCompaction(
    private val agentAiClient: AgentAiClient,
    private val applicationScope: CoroutineScope,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTxTemplate = TransactionTemplate(transactionManager)

    /**
     * Folds the backlog of the conversation [store] holds into its memory note, if it has one.
     *
     * Safe to call after every turn: a conversation whose active window still fits does nothing and
     * costs one query. Never throws — the caller is a fire-and-forget launch.
     */
    suspend fun <S : BuddyMemory> compact(store: CompactionStore<S>) {
        val plan = readTxTemplate.execute { planFor(store) } ?: return

        val request = BuddyCompactRequest(priorSummary = plan.priorSummary, folded = plan.folded)
        val memory = try {
            agentAiClient.compactBuddyMemory(request).memory
        } catch (@Suppress("SwallowedException") e: AiServiceException) {
            // The note shapes the prompt and is not the record, so a failed fold costs only a
            // longer prompt next turn. Warned, not retried.
            logger.warn("Buddy compaction skipped for {}: {}", store.label, e.message)
            return
        }

        applyFold(store, plan, memory)
    }

    /** Runs [fold] in the background. Nobody waits on a fold; one that fails costs a longer prompt next turn. */
    fun inBackground(fold: suspend () -> Unit) {
        applicationScope.launch { fold() }
    }

    private fun <S : BuddyMemory> planFor(store: CompactionStore<S>): FoldPlan? {
        val session = store.find() ?: return null
        val messages = store.transcript(session.id)
        // Folds whatever it takes to bring the active window back to CONVERSATION_WINDOW, not a fixed
        // slice, so a pass that missed its turn catches up in one call.
        val foldCount = messages.size - session.summarizedCount - CONVERSATION_WINDOW
        if (foldCount <= 0) return null
        return FoldPlan(
            sessionId = session.id,
            cursor = session.summarizedCount,
            priorSummary = session.summary,
            folded = messages
                .drop(session.summarizedCount)
                .take(foldCount),
        )
    }

    private fun <S : BuddyMemory> applyFold(store: CompactionStore<S>, plan: FoldPlan, memory: String) {
        try {
            writeTxTemplate.execute {
                val session = store.findById(plan.sessionId) ?: return@execute
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
                store.save(session)
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

/**
 * Where one kind of conversation is stored: its session, looked up for planning and again by id
 * for the swap, its transcript in order, and how a changed session is written back.
 */
class CompactionStore<S : BuddyMemory>(
    val label: String,
    val find: () -> S?,
    val findById: (UUID) -> S?,
    val transcript: (UUID) -> List<BuddyAgentMessageDto>,
    val save: (S) -> Unit,
)
