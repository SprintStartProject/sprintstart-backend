package com.sprintstart.sprintstartbackend.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.resource.UpdateBlueprintResourceResponse

fun BlueprintResource.toGetResponse(): GetBlueprintResourceResponse {
    return GetBlueprintResourceResponse(
        id = this.id,
        blueprintStepId = this.blueprintStep.id,
        revision = this.revision,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}

fun BlueprintResource.toCreateResponse(): CreateBlueprintResourceResponse {
    return CreateBlueprintResourceResponse(
        id = this.id,
        blueprintStepId = this.blueprintStep.id,
        revision = this.revision,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}

fun BlueprintResource.toUpdateResponse(): UpdateBlueprintResourceResponse {
    return UpdateBlueprintResourceResponse(
        id = this.id,
        blueprintStepId = this.blueprintStep.id,
        revision = this.revision,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}
