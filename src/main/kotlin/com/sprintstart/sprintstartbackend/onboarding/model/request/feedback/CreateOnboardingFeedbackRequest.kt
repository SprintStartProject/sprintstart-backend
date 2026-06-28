package com.sprintstart.sprintstartbackend.onboarding.model.request.feedback

import java.util.UUID

data class CreateOnboardingFeedbackRequest(
    val stepId: UUID? = null,
    val helpful: Boolean? = null,
    val message: String,
)
