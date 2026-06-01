package com.sprintstart.sprintstartbackend.onboarding.model.response.resource

import java.util.UUID

data class UpdateOnboardingResourceResponse(
    val id: UUID,
    val stepId: UUID,
    val title: String,
    val description: String,
    val url: String,
)
