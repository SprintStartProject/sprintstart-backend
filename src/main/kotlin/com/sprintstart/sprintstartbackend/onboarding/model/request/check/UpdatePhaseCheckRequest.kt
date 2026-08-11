package com.sprintstart.sprintstartbackend.onboarding.model.request.check

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import java.util.UUID

data class UpdatePhaseCheckRequest(
    val questions: List<UpdateCheckQuestionRequest> = emptyList(),
)

data class UpdateCheckQuestionRequest(
    /**
     * Identifies an existing question so it keeps its ID across the update.
     *
     * Null (or unknown) means "create a new question". Sending back the ID of a question that
     * should survive matters beyond tidiness: review pool items and stored attempt answers
     * reference questions by plain UUID, so a question that is recreated instead of updated
     * takes its whole history with it.
     */
    val id: UUID? = null,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String? = null,
    // Only used for SHORT_TEXT questions
    val correctAnswer: String? = null,
    // Only used for MULTIPLE_CHOICE questions
    val options: List<UpdateCheckOptionRequest> = emptyList(),
)

data class UpdateCheckOptionRequest(
    /**
     * Identifies an existing option so it keeps its ID. Null (or unknown) creates a new one.
     * Stored attempt answers reference selected options by UUID, so recreating an option
     * makes past answers unreadable.
     */
    val id: UUID? = null,
    val position: Int,
    val label: String,
    val correct: Boolean,
)
