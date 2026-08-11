package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhaseResponse

fun BlueprintPhase.toGetResponse(): GetBlueprintPhaseResponse {
    return GetBlueprintPhaseResponse(
        id = this.id,
        blueprintPathId = this.blueprintPath.id,
        position = this.position,
        title = this.title,
        description = this.description,
        blueprintSteps = this.blueprintSteps.map { it.toGetResponse() },
        blueprintCheckQuestions = this.blueprintCheckQuestions.map { it.toGetResponse() },
    )
}

fun BlueprintPhase.toCreateResponse(): CreateBlueprintPhaseResponse {
    return CreateBlueprintPhaseResponse(
        id = this.id,
        blueprintPathId = this.blueprintPath.id,
        position = this.position,
        title = this.title,
        description = this.description,
        blueprintSteps = this.blueprintSteps.map { it.toGetResponse() },
        blueprintCheckQuestions = this.blueprintCheckQuestions.map { it.toGetResponse() },
    )
}

fun BlueprintPhase.toUpdateResponse(): UpdateBlueprintPhaseResponse {
    return UpdateBlueprintPhaseResponse(
        id = this.id,
        blueprintPathId = this.blueprintPath.id,
        position = this.position,
        title = this.title,
        description = this.description,
        blueprintSteps = this.blueprintSteps.map { it.toGetResponse() },
        blueprintCheckQuestions = this.blueprintCheckQuestions.map { it.toGetResponse() },
    )
}
