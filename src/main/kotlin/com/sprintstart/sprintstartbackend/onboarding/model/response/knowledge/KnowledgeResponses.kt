package com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import java.time.Instant
import java.util.UUID

/** A human's durable answer, as served to a PM managing it or a hire reading it. */
data class CanonicalAnswerResponse(
    val id: UUID,
    val projectId: UUID,
    val question: String,
    val answer: String,
    val authorId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * One escalated question, for the PM inbox and for the hire's own view.
 *
 * Carries the resolved [answer] inline when the request has been answered, so a hire sees the reply
 * where they asked and a PM sees the queue and its resolutions in one read.
 */
data class KnowledgeRequestResponse(
    val id: UUID,
    val projectId: UUID,
    val hireId: UUID,
    val question: String,
    val status: KnowledgeRequestStatus,
    val createdAt: Instant,
    val answeredAt: Instant?,
    val answer: CanonicalAnswerResponse?,
)

fun CanonicalAnswer.toResponse(): CanonicalAnswerResponse =
    CanonicalAnswerResponse(
        id = id,
        projectId = projectId,
        question = question,
        answer = answer,
        authorId = authorId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun KnowledgeRequest.toResponse(answer: CanonicalAnswer?): KnowledgeRequestResponse =
    KnowledgeRequestResponse(
        id = id,
        projectId = projectId,
        hireId = hireId,
        question = question,
        status = status,
        createdAt = createdAt,
        answeredAt = answeredAt,
        answer = answer?.toResponse(),
    )
