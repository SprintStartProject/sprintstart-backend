package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request/response contract for the AI service's `POST /api/v1/grade-answers`
 * endpoint, which semantically grades free-text ([short-text]) answers in one batch.
 */
@Serializable
data class GradeAnswersRequest(
    val answers: List<GradeAnswerItem> = emptyList(),
)

@Serializable
data class GradeAnswerItem(
    // Correlation id echoed back in the result; the backend passes the questionId.
    val id: String,
    val question: String,
    @SerialName("reference_answer")
    val referenceAnswer: String,
    @SerialName("user_answer")
    val userAnswer: String,
)

@Serializable
data class GradeAnswersResponse(
    val results: List<GradeAnswerResult> = emptyList(),
)

@Serializable
data class GradeAnswerResult(
    val id: String,
    val correct: Boolean = false,
    val confidence: Double? = null,
    val feedback: String = "",
)
