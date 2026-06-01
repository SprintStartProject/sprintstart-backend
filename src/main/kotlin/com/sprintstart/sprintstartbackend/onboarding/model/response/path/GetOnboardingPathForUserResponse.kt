package com.sprintstart.sprintstartbackend.onboarding.model.response.path

import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import java.time.Instant
import java.util.UUID

data class GetOnboardingPathForUserResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val phases: List<GetOnboardingPhaseForUserResponse>,
)
