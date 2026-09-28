package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement

import java.util.UUID

data class DeleteBlueprintPhaseRequirementsRequest(
    val revision: Long,
    val requirementIds: Set<UUID>,
)
