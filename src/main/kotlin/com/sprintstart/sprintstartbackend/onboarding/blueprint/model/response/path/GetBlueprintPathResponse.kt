package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import java.util.UUID

data class GetBlueprintPathResponse(
    val id: UUID,
    val blueprintKey: UUID,
    val title: String,
    val description: String? = null,
    val version: Int,
    val revision: Long,
    val status: BlueprintStatus,
    val blueprintPhases: List<GetBlueprintPhaseResponse>,
)
