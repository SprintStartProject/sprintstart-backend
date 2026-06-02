package com.sprintstart.sprintstartbackend.onboarding.model.response.phase

import java.util.UUID

data class GetOnboardingPhasesResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
)
