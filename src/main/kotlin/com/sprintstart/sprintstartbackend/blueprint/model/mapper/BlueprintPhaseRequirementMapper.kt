package com.sprintstart.sprintstartbackend.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.blueprint.model.response.phase.CreateBlueprintPhaseRequirementResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.phase.GetBlueprintPhaseRequirementResponse

fun BlueprintPhaseRequirement.toCreateResponse(): CreateBlueprintPhaseRequirementResponse {
    return CreateBlueprintPhaseRequirementResponse(
        id = this.id,
        blueprintPhaseId = this.blueprintPhase.id,
        referenceId = this.referenceId,
        type = this.type,
        displayName = this.displayName,
    )
}

fun BlueprintPhaseRequirement.toGetResponse(): GetBlueprintPhaseRequirementResponse {
    return GetBlueprintPhaseRequirementResponse(
        id = this.id,
        blueprintPhaseId = this.blueprintPhase.id,
        referenceId = this.referenceId,
        type = this.type,
        displayName = this.displayName,
    )
}
