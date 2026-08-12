package com.sprintstart.sprintstartbackend.onboarding.model.request.assessment

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class AnswerAssessmentRequest(
    @NotNull
    val sessionId: UUID,
    @NotBlank
    val answer: String,
)
