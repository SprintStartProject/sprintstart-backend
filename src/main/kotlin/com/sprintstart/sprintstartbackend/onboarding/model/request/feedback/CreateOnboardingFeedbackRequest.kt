package com.sprintstart.sprintstartbackend.onboarding.model.request.feedback

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class CreateOnboardingFeedbackRequest(
    // The module page this is about. Null for feedback on onboarding in general.
    val pageId: UUID? = null,
    val helpful: Boolean? = null,
    @NotBlank
    val message: String,
)
