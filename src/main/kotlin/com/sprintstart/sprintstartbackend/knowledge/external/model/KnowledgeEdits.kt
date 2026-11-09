package com.sprintstart.sprintstartbackend.knowledge.external.model

import java.time.Instant

/**
 * A PM's answer to an escalated question.
 *
 * @property question The question reworded to read well as a lasting answer, or null to keep the hire's.
 */
data class EscalationAnswer(
    val text: String,
    val question: String? = null,
)

/**
 * New wording for a canonical answer, and when the PM last saw it.
 *
 * @property seenUpdatedAt The answer's `updatedAt` when the edit was drafted; an answer changed since is
 *   not overwritten.
 */
data class AnswerEdit(
    val question: String,
    val answer: String,
    val seenUpdatedAt: Instant,
)
