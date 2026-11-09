package com.sprintstart.sprintstartbackend.blueprint.model.request.checkquestion

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType

data class CreateBlueprintCheckQuestionRequest(
    val position: Int,
    val type: CheckQuestionType,
    val title: String,
    val question: String,
    val explanation: String?,
    val correctAnswer: String?,
    val graphX: Double?,
    val graphY: Double?,
)
