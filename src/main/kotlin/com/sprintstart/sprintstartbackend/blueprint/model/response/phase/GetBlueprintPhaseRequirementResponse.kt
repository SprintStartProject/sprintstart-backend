package com.sprintstart.sprintstartbackend.blueprint.model.response.phase

import com.sprintstart.sprintstartbackend.blueprint.external.enums.RequirementType
import java.util.UUID

data class GetBlueprintPhaseRequirementResponse(
    val id: UUID,
    val blueprintPhaseId: UUID,
    val referenceId: UUID,
    val type: RequirementType,
    val displayName: String,
)
