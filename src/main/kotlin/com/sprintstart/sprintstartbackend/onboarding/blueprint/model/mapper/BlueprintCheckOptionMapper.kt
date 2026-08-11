package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse

fun BlueprintCheckOption.toGetResponse(): GetBlueprintCheckOptionResponse {
    return GetBlueprintCheckOptionResponse(
        id = this.id,
        blueprintCheckQuestionId = this.blueprintCheckQuestion.id,
        position = this.position,
        label = this.label,
        correct = this.correct,
    )
}

fun BlueprintCheckOption.toCreateResponse(): CreateBlueprintCheckOptionResponse {
    return CreateBlueprintCheckOptionResponse(
        id = this.id,
        blueprintCheckQuestionId = this.blueprintCheckQuestion.id,
        position = this.position,
        label = this.label,
        correct = this.correct,
    )
}

fun BlueprintCheckOption.toUpdateResponse(): UpdateBlueprintCheckOptionResponse {
    return UpdateBlueprintCheckOptionResponse(
        id = this.id,
        blueprintCheckQuestionId = this.blueprintCheckQuestion.id,
        position = this.position,
        label = this.label,
        correct = this.correct,
    )
}
