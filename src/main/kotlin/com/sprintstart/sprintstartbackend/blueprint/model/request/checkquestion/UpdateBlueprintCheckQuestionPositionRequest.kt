package com.sprintstart.sprintstartbackend.blueprint.model.request.checkquestion

data class UpdateBlueprintCheckQuestionPositionRequest(
    val revision: Long,
    val position: Int,
)
