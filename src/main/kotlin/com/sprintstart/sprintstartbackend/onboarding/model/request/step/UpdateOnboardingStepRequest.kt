package com.sprintstart.sprintstartbackend.onboarding.model.request.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType

data class UpdateOnboardingStepRequest(
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val status: StepStatus,
    val skipReason: String?,
)
