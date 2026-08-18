package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion

data class UpdateBlueprintCheckQuestionPositionRequest(
    val revision: Long,
    val position: Int,
)
