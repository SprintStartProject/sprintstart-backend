package com.sprintstart.sprintstartbackend.path.model.response.phase

import java.util.UUID

data class CreateOnboardingPhaseResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID> = emptySet(),
)
