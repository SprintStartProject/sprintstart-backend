package com.sprintstart.sprintstartbackend.knowledge.external

import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.QuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import java.time.Instant
import java.util.UUID

/**
 * The questions users ask in a project's chats: recorded by the assistant that hears them, read by the
 * features that learn from them, such as the FAQ insight.
 *
 * Only questions asked in a project's chat are kept. Each is stored without a selection the user quoted
 * into their message, so what is read here is what listeners of
 * [com.sprintstart.sprintstartbackend.knowledge.external.event.QuestionAskedEvent] were told.
 */
interface QuestionLogApi {
    /** Keeps [question] and announces it with a `QuestionAskedEvent`. */
    fun record(question: NewQuestion)

    /** Drops the question asked in this chat message, if it was one. */
    fun forget(question: QuestionId)

    /** Drops every question asked in this conversation. */
    fun forgetConversation(conversation: BuddySessionId)

    /** Every question asked in [project]'s chats. */
    fun questionsFor(project: ProjectId): List<AskedQuestion>

    /**
     * Counts the questions [questionsFor] would return, optionally only those asked at or after [since].
     *
     * Exists so a caller can tell a user how much material an operation would work on without loading
     * every question's text to find out.
     */
    fun countFor(project: ProjectId, since: Instant? = null): Long
}

/**
 * A question to record.
 *
 * @property id The chat message it was asked in, which identifies it in the log.
 * @property conversation The conversation the message belongs to.
 * @property text The question, without any selection the user quoted into their message.
 */
data class NewQuestion(
    val id: QuestionId,
    val conversation: BuddySessionId,
    val askedBy: UserId,
    val project: ProjectId,
    val text: String,
    val askedAt: Instant,
)

/**
 * A recorded question.
 *
 * @property id The chat message it was asked in.
 * @property askedAt When it was asked. Consumers that group questions have no other way to tell a topic
 * that is picking up from one that has gone quiet, since the grouping itself carries no time.
 */
data class AskedQuestion(
    val id: UUID,
    val text: String,
    val askedAt: Instant,
)
