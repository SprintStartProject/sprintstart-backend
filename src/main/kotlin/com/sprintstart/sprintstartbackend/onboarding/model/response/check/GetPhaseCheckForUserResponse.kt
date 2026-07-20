package com.sprintstart.sprintstartbackend.onboarding.model.response.check

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import java.util.UUID

/**
 * Knowledge check of a phase as seen by the user taking it. Never exposes correct answers.
 */
data class GetPhaseCheckForUserResponse(
    val phaseId: UUID,
    val required: Boolean,
    val passed: Boolean,
    val latestAttemptId: UUID?,
    val questions: List<CheckQuestionForUserResponse>,
)

data class CheckQuestionForUserResponse(
    val id: UUID,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val options: List<CheckOptionForUserResponse> = emptyList(),
    // True when this question is a carried-over repeat from an earlier phase.
    val review: Boolean = false,
    val reviewSourcePhaseTitle: String? = null,
)

data class CheckOptionForUserResponse(
    val id: UUID,
    val position: Int,
    val label: String,
)
