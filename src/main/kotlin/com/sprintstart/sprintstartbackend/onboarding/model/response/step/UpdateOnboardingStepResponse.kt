package com.sprintstart.sprintstartbackend.onboarding.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import java.time.Instant
import java.util.UUID

data class UpdateOnboardingStepResponse(
    val id: UUID,
    val phaseId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val status: StepStatus,
    val completedAt: Instant?,
    val skipReason: String?,
)
