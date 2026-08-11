package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.UpdateBlueprintPathResponse

fun BlueprintPath.toGetOverviewResponse(): GetBlueprintPathOverviewResponse {
    return GetBlueprintPathOverviewResponse(
        id = this.id,
        blueprintKey = this.blueprintKey,
        title = this.title,
        description = this.description,
        version = this.version,
        revision = this.revision,
        status = this.status,
    )
}

fun BlueprintPath.toGetResponse(): GetBlueprintPathResponse {
    return GetBlueprintPathResponse(
        id = this.id,
        blueprintKey = this.blueprintKey,
        title = this.title,
        description = this.description,
        version = this.version,
        revision = this.revision,
        status = this.status,
        blueprintPhases = this.blueprintPhases.map { it.toGetResponse() },
    )
}

fun BlueprintPath.toCreateResponse(): CreateBlueprintPathResponse {
    return CreateBlueprintPathResponse(
        id = this.id,
        blueprintKey = this.blueprintKey,
        title = this.title,
        description = this.description,
        version = this.version,
        revision = this.revision,
        status = this.status,
        blueprintPhases = this.blueprintPhases.map { it.toGetResponse() },
    )
}

fun BlueprintPath.toUpdateResponse(): UpdateBlueprintPathResponse {
    return UpdateBlueprintPathResponse(
        id = this.id,
        blueprintKey = this.blueprintKey,
        title = this.title,
        description = this.description,
        version = this.version,
        revision = this.revision,
        status = this.status,
        blueprintPhases = this.blueprintPhases.map { it.toGetResponse() },
    )
}
