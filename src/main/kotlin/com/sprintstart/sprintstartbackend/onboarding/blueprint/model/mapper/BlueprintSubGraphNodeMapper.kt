package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintSubGraphNodeType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.AddBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.GetBlueprintSubGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodePositionResponse
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

fun BlueprintSubGraphNode.toAddBlockerResponse(): AddBlueprintSubGraphNodeBlockerResponse {
    return AddBlueprintSubGraphNodeBlockerResponse(
        revision = this.revision + 1,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun BlueprintSubGraphNode.toUpdatePositionResponse(): UpdateBlueprintSubGraphNodePositionResponse {
    return UpdateBlueprintSubGraphNodePositionResponse(
        revision = this.revision,
        graphX = this.graphX ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR),
        graphY = this.graphY ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR),
    )
}

fun BlueprintSubGraphNode.toRemoveBlockerResponse(): RemoveBlueprintSubGraphNodeBlockerResponse {
    return RemoveBlueprintSubGraphNodeBlockerResponse(
        revision = this.revision + 1,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun BlueprintSubGraphNode.toRemovePositionResponse(): RemoveBlueprintSubGraphNodePositionResponse {
    return RemoveBlueprintSubGraphNodePositionResponse(
        revision = this.revision,
    )
}

fun BlueprintSubGraphNode.toGetSubGraphNodeResponse(): GetBlueprintSubGraphNodeResponse {
    return GetBlueprintSubGraphNodeResponse(
        id = this.id,
        revision = this.revision,
        type = when (this) {
            is BlueprintStep -> BlueprintSubGraphNodeType.STEP

            is BlueprintCheckQuestion -> BlueprintSubGraphNodeType.QUESTION

            else -> throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unknown blueprint type: $this",
            )
        },
        title = this.title, // Todo: think about adding an actual title to question
        blueprintPhaseId = this.blueprintPhase.id,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}
