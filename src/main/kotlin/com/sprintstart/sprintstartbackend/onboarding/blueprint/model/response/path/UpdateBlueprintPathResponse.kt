package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import java.util.UUID

data class UpdateBlueprintPathResponse(
    val id: UUID,
    val blueprintKey: UUID,
    val version: Int,
    val revision: Long,
    val title: String,
    val description: String? = null,
    val status: BlueprintStatus,
    val blueprintPhases: List<GetBlueprintPhaseResponse>,
)
