package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse

fun BlueprintCheckQuestion.toGetResponse(): GetBlueprintCheckQuestionResponse {
    return GetBlueprintCheckQuestionResponse(
        id = this.id,
        blueprintPhaseId = blueprintPhase.id,
        revision = this.revision,
        position = this.position,
        type = this.type,
        question = this.question,
        explanation = this.explanation,
        correctAnswer = this.correctAnswer,
        blueprintCheckOptions = this.blueprintCheckOptions.map { it.toGetResponse() },
    )
}

fun BlueprintCheckQuestion.toCreateResponse(): CreateBlueprintCheckQuestionResponse {
    return CreateBlueprintCheckQuestionResponse(
        id = this.id,
        blueprintPhaseId = blueprintPhase.id,
        revision = this.revision,
        position = this.position,
        type = this.type,
        question = this.question,
        explanation = this.explanation,
        correctAnswer = this.correctAnswer,
        blueprintCheckOptions = this.blueprintCheckOptions.map { it.toGetResponse() },
    )
}

fun BlueprintCheckQuestion.toUpdateResponse(): UpdateBlueprintCheckQuestionResponse {
    return UpdateBlueprintCheckQuestionResponse(
        id = this.id,
        blueprintPhaseId = blueprintPhase.id,
        revision = this.revision,
        position = this.position,
        type = this.type,
        question = this.question,
        explanation = this.explanation,
        correctAnswer = this.correctAnswer,
        blueprintCheckOptions = this.blueprintCheckOptions.map { it.toGetResponse() },
    )
}

fun BlueprintCheckQuestion.toUpdatePositionResponse(): UpdateBlueprintCheckQuestionPositionResponse {
    return UpdateBlueprintCheckQuestionPositionResponse(
        id = this.id,
        revision = this.revision,
        position = this.position,
    )
}
