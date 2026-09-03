package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step

data class UpdateBlueprintStepGraphPositionResponse(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
