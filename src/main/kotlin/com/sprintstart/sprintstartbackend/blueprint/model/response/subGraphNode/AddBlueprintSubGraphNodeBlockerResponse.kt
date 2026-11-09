package com.sprintstart.sprintstartbackend.blueprint.model.response.subGraphNode

import java.util.UUID

data class AddBlueprintSubGraphNodeBlockerResponse(
    val revision: Long,
    val blockerIds: Set<UUID>,
)
