package com.sprintstart.sprintstartbackend.blueprint.model.response.phase

import com.sprintstart.sprintstartbackend.blueprint.model.response.graphNode.UpdateBlueprintGraphNodeResponse

data class DeleteBlueprintPhaseResponse(
    val updatedPhases: List<UpdateBlueprintGraphNodeResponse>,
)
