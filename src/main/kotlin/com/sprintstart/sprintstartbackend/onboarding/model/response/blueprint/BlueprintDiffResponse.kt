package com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint

data class BlueprintDiffResponse(
    val scope: String,
    val changes: List<DiffChangeResponse>,
    val blocked: Boolean = false,
)

data class DiffChangeResponse(
    val action: String,
    val stepId: String? = null,
    val description: String? = null,
)
