package com.sprintstart.sprintstartbackend.onboarding.model.response.feedback

import java.time.Instant
import java.util.UUID

data class GetAdminOnboardingFeedbackResponse(
    val id: UUID,
    val userId: UUID,
    val stepId: UUID?,
    val stepTitle: String?,
    val message: String,
    val read: Boolean,
    val createdAt: Instant,
)
