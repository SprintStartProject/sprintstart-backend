package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task

data class UpdateBlueprintTaskPositionRequest(
    val revision: Long,
    val position: Int,
)
