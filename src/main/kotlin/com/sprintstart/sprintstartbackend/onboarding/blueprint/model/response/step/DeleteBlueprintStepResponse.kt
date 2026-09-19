package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodeResponse

data class DeleteBlueprintStepResponse(
    val updatedSteps: List<UpdateBlueprintSubGraphNodeResponse>,
)
