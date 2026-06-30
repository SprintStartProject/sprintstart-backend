package com.sprintstart.sprintstartbackend.onboarding.model.request.feedback

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreateOnboardingFeedbackRequest(
    @NotNull
    val stepId: UUID? = null,
    val helpful: Boolean? = null,
    @NotBlank
    val message: String,
)
