package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode

data class UpdateBlueprintSubGraphNodePositionRequest(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
