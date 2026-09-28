package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode

data class UpdateBlueprintGraphNodePositionResponse(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
