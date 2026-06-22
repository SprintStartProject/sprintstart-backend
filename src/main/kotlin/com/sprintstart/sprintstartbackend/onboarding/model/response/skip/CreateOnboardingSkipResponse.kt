package com.sprintstart.sprintstartbackend.onboarding.model.response.skip

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import java.time.Instant
import java.util.UUID

data class CreateOnboardingSkipResponse(
    val id: UUID,
    val stepId: UUID,
    val status: SkipStatus,
    val reason: String,
    val reviewCommend: String? = null,
    val createdAt: Instant,
    val reviewedAt: Instant? = null,
)
