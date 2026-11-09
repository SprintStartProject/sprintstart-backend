package com.sprintstart.sprintstartbackend.path.model.response.skip

import com.sprintstart.sprintstartbackend.path.external.enums.SkipStatus
import java.time.Instant
import java.util.UUID

data class GetOnboardingSkipResponse(
    val id: UUID,
    val stepId: UUID,
    val status: SkipStatus,
    val reason: String,
    val reviewComment: String? = null,
    val createdAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
)
