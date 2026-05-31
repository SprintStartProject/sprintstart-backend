package com.sprintstart.sprintstartbackend.onboarding.model.response

import java.time.Instant
import java.util.UUID

data class GetOnboardingPathResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val phases: List<GetOnboardingPhaseResponse>,
)

