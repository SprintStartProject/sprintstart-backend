package com.sprintstart.sprintstartbackend.onboarding.model.response.question

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import java.util.UUID

/**
 * A knowledge-check question as seen by the user taking it. Never exposes correct answers;
 * those are only revealed in the submit result.
 */
data class GetOnboardingQuestionForUserResponse(
    val id: UUID,
    val phaseId: UUID,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val options: List<QuestionOptionForUserResponse> = emptyList(),
    val status: QuestionStatus,
    val title: String = question,
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID> = emptySet(),
)

data class QuestionOptionForUserResponse(
    val id: UUID,
    val position: Int,
    val label: String,
)
