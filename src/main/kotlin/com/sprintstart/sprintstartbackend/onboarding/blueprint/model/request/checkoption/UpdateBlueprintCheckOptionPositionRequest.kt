package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption

data class UpdateBlueprintCheckOptionPositionRequest(
    val revision: Long,
    val position: Int,
)
