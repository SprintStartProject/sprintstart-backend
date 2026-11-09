package com.sprintstart.sprintstartbackend.blueprint.model.request.subGraphNode

data class UpdateBlueprintSubGraphNodePositionRequest(
    val revision: Long,
    val graphX: Double,
    val graphY: Double,
)
