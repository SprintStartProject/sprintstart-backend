package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode

import java.util.UUID

data class GetBlueprintGraphResponse(
    val nodes: List<GetBlueprintGraphNodeResponse>,
)

data class GetBlueprintGraphNodeResponse(
    val id: UUID,
    val revision: Long,
    val title: String,
    val blueprintPathId: UUID,
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID>,
)
