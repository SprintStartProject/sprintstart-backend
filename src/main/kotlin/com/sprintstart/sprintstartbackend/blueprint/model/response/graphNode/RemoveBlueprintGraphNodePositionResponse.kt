package com.sprintstart.sprintstartbackend.blueprint.model.response.graphNode

import java.util.UUID

data class RemoveBlueprintGraphNodePositionResponse(
    val changedNodes: List<UpdateBlueprintGraphNodeResponse>,
)

data class UpdateBlueprintGraphNodeResponse(
    val id: UUID,
    val revision: Long,
)
