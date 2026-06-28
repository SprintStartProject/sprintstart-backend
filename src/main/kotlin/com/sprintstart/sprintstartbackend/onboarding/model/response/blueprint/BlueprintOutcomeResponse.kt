package com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint

data class BlueprintOutcomeResponse(
    val scope: String,
    val status: String,
    val message: String? = null,
)
