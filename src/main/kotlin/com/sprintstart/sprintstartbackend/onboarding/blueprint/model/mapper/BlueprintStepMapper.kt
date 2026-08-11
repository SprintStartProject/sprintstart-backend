package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse

fun BlueprintStep.toGetResponse(): GetBlueprintStepResponse {
    return GetBlueprintStepResponse(
        id = this.id,
        blueprintPhaseId = this.blueprintPhase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        type = this.type,
        aiAssisted = this.aiAssisted,
        estimatedMinutes = this.estimatedMinutes,
        expectedOutcome = this.expectedOutcome,
        blueprintTasks = this.blueprintTasks.map { it.toGetResponse() },
        blueprintResources = this.blueprintResources.map { it.toGetResponse() },
    )
}

fun BlueprintStep.toCreateResponse(): CreateBlueprintStepResponse {
    return CreateBlueprintStepResponse(
        id = this.id,
        blueprintPhaseId = this.blueprintPhase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        type = this.type,
        aiAssisted = this.aiAssisted,
        estimatedMinutes = this.estimatedMinutes,
        expectedOutcome = this.expectedOutcome,
        blueprintTasks = this.blueprintTasks.map { it.toGetResponse() },
        blueprintResources = this.blueprintResources.map { it.toGetResponse() },
    )
}

fun BlueprintStep.toUpdateResponse(): UpdateBlueprintStepResponse {
    return UpdateBlueprintStepResponse(
        id = this.id,
        blueprintPhaseId = this.blueprintPhase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        type = this.type,
        aiAssisted = this.aiAssisted,
        estimatedMinutes = this.estimatedMinutes,
        expectedOutcome = this.expectedOutcome,
        blueprintTasks = this.blueprintTasks.map { it.toGetResponse() },
        blueprintResources = this.blueprintResources.map { it.toGetResponse() },
    )
}
