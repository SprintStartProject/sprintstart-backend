package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase

import java.util.UUID

data class UpdateBlueprintPhasePositionResponse(
    val id: UUID,
    val revision: Long,
    val position: Int,
)
