package com.sprintstart.sprintstartbackend.onboarding.model.request.check

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType

data class UpdatePhaseCheckRequest(
    val questions: List<UpdateCheckQuestionRequest> = emptyList(),
)

data class UpdateCheckQuestionRequest(
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
    val position: Int,
    val label: String,
    val correct: Boolean,
)
