package com.sprintstart.sprintstartbackend.blueprint.model.response.subGraphNode

import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintSubGraphNodeType
import java.util.UUID

data class GetBlueprintSubGraphResponse(
    val nodes: List<GetBlueprintSubGraphNodeResponse>,
)

data class GetBlueprintSubGraphNodeResponse(
    val id: UUID,
    val revision: Long,
    val type: BlueprintSubGraphNodeType,
    val title: String,
    val blueprintPhaseId: UUID,
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID>,
)
