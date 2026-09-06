package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.UpdateBlueprintGraphNodeResponse

data class DeleteBlueprintPhaseResponse(
    val updatedPhases: List<UpdateBlueprintGraphNodeResponse>,
)
