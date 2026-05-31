package com.sprintstart.sprintstartbackend.onboarding.model.response

import java.time.Instant
import java.util.UUID

data class GetAllOnboardingPathResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val phaseCount: Int,
    val stepCount: Int,
    val finishedStepCount: Int,
)
