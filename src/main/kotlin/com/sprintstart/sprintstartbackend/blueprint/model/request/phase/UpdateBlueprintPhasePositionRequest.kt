package com.sprintstart.sprintstartbackend.blueprint.model.request.phase

data class UpdateBlueprintPhasePositionRequest(
    val revision: Long,
    val position: Int,
)
