package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase

data class UpdateBlueprintPhasePositionRequest(
    val revision: Long,
    val position: Int,
)
