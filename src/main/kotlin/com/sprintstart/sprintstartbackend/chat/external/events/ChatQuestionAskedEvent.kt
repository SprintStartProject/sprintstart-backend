package com.sprintstart.sprintstartbackend.chat.external.events

import java.time.Instant
import java.util.UUID

/**
 * Published when a user asks the AI Buddy a question.
 *
 * Carries the question text rather than only its id so listeners do not have to reach back into
 * the chat module's storage for it. Handling is expected to be asynchronous: the prompt's response
 * stream must never wait on an analytics consumer.
 *
 * @property messageId the chat message the question was asked in; a listener that acts on the
 * event more than once should use this to stay idempotent
 * @property projectId the project the chat belongs to
 * @property question the raw question text, unredacted
 * @property askedAt when the question was asked
 */
data class ChatQuestionAskedEvent(
    val messageId: UUID,
    val chatId: UUID,
    val projectId: UUID,
    val question: String,
    val askedAt: Instant,
)
