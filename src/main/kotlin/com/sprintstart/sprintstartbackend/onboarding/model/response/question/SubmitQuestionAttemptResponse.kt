package com.sprintstart.sprintstartbackend.onboarding.model.response.question

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import java.time.Instant
import java.util.UUID

/**
 * Grading result of a submitted question attempt. This is the only user-facing place
 * where correct answers are revealed.
 */
data class SubmitQuestionAttemptResponse(
    val attemptId: UUID,
    val questionId: UUID,
    val correct: Boolean,
    val createdAt: Instant,
    val correctOptionIds: List<UUID> = emptyList(),
    val correctAnswer: String? = null,
    val explanation: String? = null,
    // AI-generated feedback for short-text answers; null for multiple choice.
    val feedback: String? = null,
    val status: QuestionStatus,
    // True when this attempt completed the entire onboarding journey.
    val onboardingCompleted: Boolean = false,
)
