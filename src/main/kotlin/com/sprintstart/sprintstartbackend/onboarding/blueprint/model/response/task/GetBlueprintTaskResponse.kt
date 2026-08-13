package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task

import java.util.UUID

data class GetBlueprintTaskResponse(
    val id: UUID,
    val blueprintStepId: UUID,
    val revision: Long,
    val position: Int,
    val title: String,
    val description: String,
)
