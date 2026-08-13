package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption

data class UpdateBlueprintCheckOptionRequest(
    val revision: Long,
    val position: Int,
    val label: String,
    val correct: Boolean,
)
