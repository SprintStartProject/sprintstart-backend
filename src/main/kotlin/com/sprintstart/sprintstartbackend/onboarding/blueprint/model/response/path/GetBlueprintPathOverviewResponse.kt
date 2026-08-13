package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import java.util.UUID

data class GetBlueprintPathOverviewResponse(
    val id: UUID,
    val blueprintKey: UUID,
    val version: Int,
    val revision: Long,
    val title: String,
    val description: String? = null,
    val status: BlueprintStatus,
)
