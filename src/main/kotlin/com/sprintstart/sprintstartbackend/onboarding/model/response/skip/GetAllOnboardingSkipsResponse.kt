package com.sprintstart.sprintstartbackend.onboarding.model.response.skip

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import java.time.Instant
import java.util.UUID

data class GetAllOnboardingSkipsResponse(
    val id: UUID,
    val stepId: UUID,
    val status: SkipStatus,
    val reason: String,
    val reviewComment: String? = null,
    val createdAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
)
