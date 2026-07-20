package com.sprintstart.sprintstartbackend.onboarding.model.response.check

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import java.util.UUID

/**
 * Knowledge check of a phase for admin-facing editing screens, including correct answers.
 */
data class GetPhaseCheckResponse(
    val phaseId: UUID,
    val questions: List<CheckQuestionResponse>,
)

data class CheckQuestionResponse(
    val id: UUID,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String?,
    val correctAnswer: String? = null,
    val options: List<CheckOptionResponse> = emptyList(),
)

data class CheckOptionResponse(
    val id: UUID,
    val position: Int,
    val label: String,
    val correct: Boolean,
)
