package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import java.util.UUID

data class CreateBlueprintPhaseRequirementsResponse(
    val revision: Long,
    val requirements: Set<CreateBlueprintPhaseRequirementResponse>,
)

data class CreateBlueprintPhaseRequirementResponse(
    val id: UUID,
    val blueprintPhaseId: UUID,
    val referenceId: UUID,
    val type: RequirementType,
    val displayName: String,
)
