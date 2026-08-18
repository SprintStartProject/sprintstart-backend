package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step

import java.util.UUID

data class UpdateBlueprintStepPositionResponse(
    val id: UUID,
    val revision: Long,
    val position: Int,
)
