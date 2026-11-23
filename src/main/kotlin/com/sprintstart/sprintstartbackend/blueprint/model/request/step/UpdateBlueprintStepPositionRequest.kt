package com.sprintstart.sprintstartbackend.blueprint.model.request.step

data class UpdateBlueprintStepPositionRequest(
    val revision: Long,
    val position: Int,
)
