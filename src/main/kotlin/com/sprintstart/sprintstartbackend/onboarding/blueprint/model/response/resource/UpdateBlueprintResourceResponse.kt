package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource

import java.util.UUID

data class UpdateBlueprintResourceResponse(
    val id: UUID,
    val blueprintStepId: UUID,
    val revision: Long,
    val title: String,
    val description: String,
    val url: String,
)
