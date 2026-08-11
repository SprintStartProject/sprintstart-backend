package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType

data class CreateBlueprintStepRequest(
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
)
