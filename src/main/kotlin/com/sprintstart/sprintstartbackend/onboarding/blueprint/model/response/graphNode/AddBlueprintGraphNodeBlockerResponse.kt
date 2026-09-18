package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode

import java.util.UUID

data class AddBlueprintGraphNodeBlockerResponse(
    val revision: Long,
    val blockerIds: Set<UUID>,
)
