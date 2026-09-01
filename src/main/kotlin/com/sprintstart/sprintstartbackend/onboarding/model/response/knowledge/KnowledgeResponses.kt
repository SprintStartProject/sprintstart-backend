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
 * Who asked, and where they are — so a PM can answer without going to look them up.
 *
 * Served on the PM queue only. A hire reading their own escalations already knows who they are, and
 * this carries a name and a progress figure that nobody outside the project has any business seeing.
 */
data class EscalationHireResponse(
    val userId: UUID,
    val displayName: String,
    val profileIcon: String?,
    val currentPhase: String?,
    val currentStep: String?,
    val progressPercentage: Double,
)

/**
 * One escalated question, for the PM inbox and for the hire's own view.
 *
 * Carries the resolved [answer] inline when the request has been answered, so a hire sees the reply
 * where they asked and a PM sees the queue and its resolutions in one read.
 *
 * [hire] is populated for the PM inbox and left null everywhere else. Null is also the answer when
 * the asker's user record can no longer be resolved: a question whose author has left the system is
 * still a question somebody is waiting on, and dropping it from the queue would hide real work.
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
    val hire: EscalationHireResponse? = null,
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

fun KnowledgeRequest.toResponse(
    answer: CanonicalAnswer?,
    hire: EscalationHireResponse? = null,
): KnowledgeRequestResponse =
    KnowledgeRequestResponse(
        id = id,
        projectId = projectId,
        hireId = hireId,
        question = question,
        status = status,
        createdAt = createdAt,
        answeredAt = answeredAt,
        answer = answer?.toResponse(),
        hire = hire,
    )
