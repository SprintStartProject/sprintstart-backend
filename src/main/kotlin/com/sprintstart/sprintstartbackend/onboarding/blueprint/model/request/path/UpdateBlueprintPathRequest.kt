package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path

data class UpdateBlueprintPathRequest(
    val title: String,
    val description: String,
    val version: Int,
    val revision: Long,
)
