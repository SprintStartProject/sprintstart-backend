package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task

import java.util.UUID

data class CreateBlueprintTaskResponse(
    val id: UUID,
    val blueprintStepId: UUID,
    val position: Int,
    val title: String,
    val description: String,
)
