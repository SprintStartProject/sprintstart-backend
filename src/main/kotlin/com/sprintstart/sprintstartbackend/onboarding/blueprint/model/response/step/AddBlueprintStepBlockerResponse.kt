package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step

import java.util.UUID

data class AddBlueprintStepBlockerResponse(
    val revision: Long,
    val blockerIds: Set<UUID>,
)
