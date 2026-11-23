package com.sprintstart.sprintstartbackend.blueprint.model.response.step

import com.sprintstart.sprintstartbackend.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodeResponse

data class DeleteBlueprintStepResponse(
    val updatedSteps: List<UpdateBlueprintSubGraphNodeResponse>,
)
