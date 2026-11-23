package com.sprintstart.sprintstartbackend.blueprint.model.response.step

import com.sprintstart.sprintstartbackend.blueprint.external.enums.StepType
import com.sprintstart.sprintstartbackend.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.task.GetBlueprintTaskResponse
import java.util.UUID

data class CreateBlueprintStepResponse(
    val id: UUID,
    val blueprintPhaseId: UUID,
    val revision: Long,
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val aiAssisted: Boolean,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val blockerIds: Set<UUID>,
    val graphX: Double?,
    val graphY: Double?,
    val blueprintTasks: List<GetBlueprintTaskResponse>,
    val blueprintResources: List<GetBlueprintResourceResponse>,
)
