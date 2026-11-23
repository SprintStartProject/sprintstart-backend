package com.sprintstart.sprintstartbackend.blueprint.model.response.subGraphNode

import java.util.UUID

data class RemoveBlueprintSubGraphNodeBlockerResponse(
    val revision: Long,
    val blockerIds: Set<UUID>,
)
