package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase

data class CreateBlueprintPhaseRequest(
    val position: Int,
    val title: String,
    val description: String,
)
