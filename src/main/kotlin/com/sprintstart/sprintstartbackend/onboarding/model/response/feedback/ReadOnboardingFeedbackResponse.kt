package com.sprintstart.sprintstartbackend.onboarding.model.response.feedback

import java.util.UUID

data class ReadOnboardingFeedbackResponse(
    val id: UUID,
    val read: Boolean,
)
