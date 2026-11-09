package com.sprintstart.sprintstartbackend.blueprint.model.request.checkquestion

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType

data class UpdateBlueprintCheckQuestionRequest(
    val revision: Long,
    val title: String,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String?,
    val correctAnswer: String?,
)
