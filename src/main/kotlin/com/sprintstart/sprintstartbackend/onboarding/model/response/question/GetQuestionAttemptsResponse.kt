package com.sprintstart.sprintstartbackend.onboarding.model.response.question

import java.time.Instant
import java.util.UUID

/**
 * Submitted attempts of a user on one question, for admin/PM/HR review.
 */
data class GetQuestionAttemptsResponse(
    val userId: UUID,
    val questionId: UUID,
    val attempts: List<QuestionAttemptResponse>,
)

data class QuestionAttemptResponse(
    val id: UUID,
    val correct: Boolean,
    val createdAt: Instant,
    val selectedOptionIds: List<UUID> = emptyList(),
    val textAnswer: String?,
)
