package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode

import java.util.UUID

data class RemoveBlueprintSubGraphNodePositionResponse(
    val updatedNodes: List<UpdateBlueprintSubGraphNodeResponse>,
)

data class UpdateBlueprintSubGraphNodeResponse(
    val id: UUID,
    val revision: Long,
)
