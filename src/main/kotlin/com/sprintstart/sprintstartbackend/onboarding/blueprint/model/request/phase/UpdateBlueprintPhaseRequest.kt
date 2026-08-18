package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType

data class UpdateBlueprintPhaseRequest(
    val revision: Long,
    val position: Int,
    val title: String,
    val description: String?,
    val aiPrompt: String?,
    val type: BlueprintPhaseType,
)
