package com.sprintstart.sprintstartbackend.blueprint.model.request.phaseRequirement

import com.sprintstart.sprintstartbackend.blueprint.external.enums.RequirementType
import java.util.UUID

data class CreateBlueprintPhaseRequirementsRequest(
    val revision: Long,
    val requirements: Set<CreateBlueprintPhaseRequirementRequest>,
)

data class CreateBlueprintPhaseRequirementRequest(
    val referenceId: UUID,
    val type: RequirementType,
)
