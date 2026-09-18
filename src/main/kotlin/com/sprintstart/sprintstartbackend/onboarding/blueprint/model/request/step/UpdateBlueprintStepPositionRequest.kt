package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step

data class UpdateBlueprintStepPositionRequest(
    val revision: Long,
    val position: Int,
)
