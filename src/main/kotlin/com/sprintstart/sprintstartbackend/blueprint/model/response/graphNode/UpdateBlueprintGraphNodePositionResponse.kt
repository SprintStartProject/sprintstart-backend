package com.sprintstart.sprintstartbackend.blueprint.model.response.graphNode

data class UpdateBlueprintGraphNodePositionResponse(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
