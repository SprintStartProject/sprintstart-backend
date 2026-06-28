package com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint

data class BlueprintStepResponse(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    val requirement: String = "recommended",
    val invariant: Boolean = false,
)
