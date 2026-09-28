package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType

data class UpdateBlueprintStepRequest(
    val revision: Long,
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val aiAssisted: Boolean,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
)
