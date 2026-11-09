package com.sprintstart.sprintstartbackend.knowledge.external.event

import java.time.Instant
import java.util.UUID

/**
 * Published when a question has been recorded in the question log.
 *
 * Carries the question text rather than only its id so listeners do not have to read the log back for
 * it. Handling is expected to be asynchronous: the chat's response stream must never wait on an
 * analytics consumer.
 *
 * @property messageId the chat message the question was asked in; a listener that acts on the event
 * more than once should use this to stay idempotent
 * @property projectId the project the chat belongs to
 * @property question the question text without a quoted selection, unredacted
 * @property askedAt when the question was asked
 */
data class QuestionAskedEvent(
    val messageId: UUID,
    val sessionId: UUID,
    val projectId: UUID,
    val question: String,
    val askedAt: Instant,
)
