package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import java.util.UUID

data class CreateBlueprintPhaseRequirementsRequest(
    val revision: Long,
    val requirements: Set<CreateBlueprintPhaseRequirementRequest>,
)

data class CreateBlueprintPhaseRequirementRequest(
    val referenceId: UUID,
    val type: RequirementType,
)
