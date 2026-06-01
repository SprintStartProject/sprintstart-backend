package com.sprintstart.sprintstartbackend.onboarding.model.response.path

import java.time.Instant
import java.util.UUID

data class CreateOnboardingPathResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
)
