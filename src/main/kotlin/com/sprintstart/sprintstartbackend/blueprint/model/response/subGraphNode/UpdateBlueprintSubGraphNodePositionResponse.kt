package com.sprintstart.sprintstartbackend.blueprint.model.response.subGraphNode

data class UpdateBlueprintSubGraphNodePositionResponse(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
