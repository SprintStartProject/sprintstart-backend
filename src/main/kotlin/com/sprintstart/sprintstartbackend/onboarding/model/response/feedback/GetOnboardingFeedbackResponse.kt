package com.sprintstart.sprintstartbackend.onboarding.model.response.feedback

import java.time.Instant
import java.util.UUID

data class GetOnboardingFeedbackResponse(
    val id: UUID,
    val stepId: UUID?,
    val helpful: Boolean?,
    val comment: String,
    val createdAt: Instant,
)
