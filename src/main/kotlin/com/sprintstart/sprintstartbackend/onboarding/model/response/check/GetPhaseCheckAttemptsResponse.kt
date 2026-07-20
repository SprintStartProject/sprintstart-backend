package com.sprintstart.sprintstartbackend.onboarding.model.response.check

import java.time.Instant
import java.util.UUID

/**
 * Submitted knowledge check attempts of a user for one phase, for admin/PM/HR review.
 */
data class GetPhaseCheckAttemptsResponse(
    val userId: UUID,
    val phaseId: UUID,
    val attempts: List<CheckAttemptResponse>,
)

data class CheckAttemptResponse(
    val id: UUID,
    val passed: Boolean,
    val createdAt: Instant,
    val correctAnswerCount: Int,
    val questionCount: Int,
    val answers: List<CheckAttemptAnswerResponse>,
)

data class CheckAttemptAnswerResponse(
    val questionId: UUID,
    val selectedOptionIds: List<UUID> = emptyList(),
    val textAnswer: String?,
    val correct: Boolean,
)
