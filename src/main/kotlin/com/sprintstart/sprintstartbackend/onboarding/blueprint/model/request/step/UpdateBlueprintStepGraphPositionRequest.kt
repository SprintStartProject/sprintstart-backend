package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step

data class UpdateBlueprintStepGraphPositionRequest(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
