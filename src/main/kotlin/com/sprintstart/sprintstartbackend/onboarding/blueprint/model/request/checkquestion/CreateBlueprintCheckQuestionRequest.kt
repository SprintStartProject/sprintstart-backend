package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType

data class CreateBlueprintCheckQuestionRequest(
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String?,
    val correctAnswer: String?,
)
