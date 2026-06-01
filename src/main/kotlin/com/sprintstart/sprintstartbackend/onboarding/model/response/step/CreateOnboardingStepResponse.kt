package com.sprintstart.sprintstartbackend.onboarding.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import java.util.UUID

data class CreateOnboardingStepResponse(
    val id: UUID,
    val phaseId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val status: StepStatus,
)
