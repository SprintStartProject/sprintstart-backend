package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import java.util.UUID

data class DeleteBlueprintPhaseRequirementsRequest(
    val revision: Long,
    val requirementIds: Set<UUID>,
)
