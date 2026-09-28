package com.sprintstart.sprintstartbackend.onboarding.model.response.skip

import java.time.Instant
import java.util.UUID

data class GetOnboardingStepSkipResponse(
    val id: UUID,
    val stepId: UUID,
    val reason: String,
    val accepted: Boolean?,
    val reviewComment: String?,
    val reviewedAt: Instant?,
    /** When the member saw the review; null while it is new to them (or still pending). */
    val answerSeenAt: Instant? = null,
)
