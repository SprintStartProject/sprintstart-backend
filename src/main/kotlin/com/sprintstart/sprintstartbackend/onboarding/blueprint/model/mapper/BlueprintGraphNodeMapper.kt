package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.AddBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.GetBlueprintGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.UpdateBlueprintGraphNodePositionResponse
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

fun BlueprintPhase.toGetGraphNodeResponse(): GetBlueprintGraphNodeResponse {
    return GetBlueprintGraphNodeResponse(
        id = this.id,
        revision = this.revision,
        title = this.title,
        blueprintPathId = this.blueprintPath.id,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun BlueprintPhase.toAddBlockerResponse(): AddBlueprintGraphNodeBlockerResponse {
    return AddBlueprintGraphNodeBlockerResponse(
        revision = this.revision + 1,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun BlueprintPhase.toUpdateGraphPositionResponse(): UpdateBlueprintGraphNodePositionResponse {
    return UpdateBlueprintGraphNodePositionResponse(
        revision = this.revision,
        graphX = this.graphX ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR),
        graphY = this.graphY ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR),
    )
}

fun BlueprintPhase.toRemoveBlockerResponse(): RemoveBlueprintGraphNodeBlockerResponse {
    return RemoveBlueprintGraphNodeBlockerResponse(
        revision = this.revision + 1,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun BlueprintPhase.toRemoveGraphPositionResponse(): RemoveBlueprintGraphNodePositionResponse {
    return RemoveBlueprintGraphNodePositionResponse(
        revision = this.revision,
    )
}
