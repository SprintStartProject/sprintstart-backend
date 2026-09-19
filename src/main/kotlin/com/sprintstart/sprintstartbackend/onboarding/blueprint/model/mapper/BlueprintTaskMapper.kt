package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.CreateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.GetBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskResponse

fun BlueprintTask.toGetResponse(): GetBlueprintTaskResponse {
    return GetBlueprintTaskResponse(
        id = this.id,
        blueprintStepId = this.blueprintStep.id,
        revision = this.revision,
        position = this.position,
        title = this.title,
        description = this.description,
    )
}

fun BlueprintTask.toCreateResponse(): CreateBlueprintTaskResponse {
    return CreateBlueprintTaskResponse(
        id = this.id,
        blueprintStepId = this.blueprintStep.id,
        revision = this.revision,
        position = this.position,
        title = this.title,
        description = this.description,
    )
}

fun BlueprintTask.toUpdateResponse(): UpdateBlueprintTaskResponse {
    return UpdateBlueprintTaskResponse(
        id = this.id,
        blueprintStepId = this.blueprintStep.id,
        revision = this.revision,
        position = this.position,
        title = this.title,
        description = this.description,
    )
}

fun BlueprintTask.toUpdatePositionResponse(): UpdateBlueprintTaskPositionResponse {
    return UpdateBlueprintTaskPositionResponse(
        id = this.id,
        revision = this.revision,
        position = this.position,
    )
}
