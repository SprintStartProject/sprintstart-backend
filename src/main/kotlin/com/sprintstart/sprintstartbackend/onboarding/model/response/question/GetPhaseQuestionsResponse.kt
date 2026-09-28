package com.sprintstart.sprintstartbackend.onboarding.model.response.question

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import java.util.UUID

/**
 * A phase's knowledge-check questions including correct answers, for admin-facing
 * editing screens.
 */
data class GetPhaseQuestionsResponse(
    val phaseId: UUID,
    val questions: List<QuestionForAdminResponse>,
)

data class QuestionForAdminResponse(
    val id: UUID,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String?,
    val correctAnswer: String? = null,
    val options: List<QuestionOptionForAdminResponse> = emptyList(),
    val title: String = question,
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID> = emptySet(),
)

data class QuestionOptionForAdminResponse(
    val id: UUID,
    val position: Int,
    val label: String,
    val correct: Boolean,
)
