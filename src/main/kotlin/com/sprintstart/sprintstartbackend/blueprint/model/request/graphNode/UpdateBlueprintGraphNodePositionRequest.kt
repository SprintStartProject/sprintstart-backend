package com.sprintstart.sprintstartbackend.blueprint.model.request.graphNode

data class UpdateBlueprintGraphNodePositionRequest(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
