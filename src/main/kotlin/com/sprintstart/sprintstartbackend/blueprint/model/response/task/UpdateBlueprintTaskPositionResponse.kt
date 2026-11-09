package com.sprintstart.sprintstartbackend.blueprint.model.response.task

import java.util.UUID

data class UpdateBlueprintTaskPositionResponse(
    val id: UUID,
    val revision: Long,
    val position: Int,
)
