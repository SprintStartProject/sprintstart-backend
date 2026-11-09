package com.sprintstart.sprintstartbackend.blueprint.model.request.phaseRequirement

import java.util.UUID

data class DeleteBlueprintPhaseRequirementsRequest(
    val revision: Long,
    val requirementIds: Set<UUID>,
)
