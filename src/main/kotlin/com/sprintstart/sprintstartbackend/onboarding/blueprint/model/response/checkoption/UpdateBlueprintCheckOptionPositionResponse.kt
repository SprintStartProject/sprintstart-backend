package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption

import java.util.UUID

data class UpdateBlueprintCheckOptionPositionResponse(
    val id: UUID,
    val revision: Long,
    val position: Int,
)
