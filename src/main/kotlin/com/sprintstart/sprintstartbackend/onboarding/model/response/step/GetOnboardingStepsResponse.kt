package com.sprintstart.sprintstartbackend.onboarding.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import java.time.Instant
import java.util.UUID

data class GetOnboardingStepsResponse(
    val id: UUID,
    val phaseId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedMinutes: Int,
    val status: StepStatus,
    val completedAt: Instant?,
    val skipReason: String?,
)
