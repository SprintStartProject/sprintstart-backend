package com.sprintstart.sprintstartbackend.onboarding.model.request.knowledge

import java.util.UUID

/** A hire escalating a question the buddy could not answer to their project's PM. */
data class CreateKnowledgeRequest(
    val projectId: UUID,
    val question: String,
)

/**
 * A PM answering an escalated question.
 *
 * [question] is optional: it defaults to the request's original wording, but a PM may generalise it
 * so the answer matches more than the one phrasing that triggered it.
 */
data class AnswerKnowledgeRequest(
    val answer: String,
    val question: String? = null,
)

/** A PM editing a durable answer when reality changes. */
data class UpdateCanonicalAnswer(
    val question: String,
    val answer: String,
)
