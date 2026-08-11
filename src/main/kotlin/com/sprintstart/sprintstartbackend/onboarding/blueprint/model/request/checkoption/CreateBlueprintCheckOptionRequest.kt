package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption

data class CreateBlueprintCheckOptionRequest(
    val position: Int,
    val label: String,
    val correct: Boolean,
)
