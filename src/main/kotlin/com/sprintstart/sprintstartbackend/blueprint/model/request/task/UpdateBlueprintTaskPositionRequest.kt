package com.sprintstart.sprintstartbackend.blueprint.model.request.task

data class UpdateBlueprintTaskPositionRequest(
    val revision: Long,
    val position: Int,
)
