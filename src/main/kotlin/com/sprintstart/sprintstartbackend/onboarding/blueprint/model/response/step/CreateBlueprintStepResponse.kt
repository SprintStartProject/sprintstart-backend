package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.GetBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import java.util.UUID

data class CreateBlueprintStepResponse(
    val id: UUID,
    val blueprintPhaseId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val aiAssisted: Boolean,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val blueprintTasks: List<GetBlueprintTaskResponse>,
    val blueprintResources: List<GetBlueprintResourceResponse>,
)
