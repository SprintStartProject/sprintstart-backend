package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource

import java.util.UUID

data class GetBlueprintResourceResponse(
    val id: UUID,
    val blueprintStepId: UUID,
    val title: String,
    val description: String,
    val url: String,
)
