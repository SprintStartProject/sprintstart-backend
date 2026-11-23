package com.sprintstart.sprintstartbackend.path.model.response.path

import java.time.Instant
import java.util.UUID

data class GetOnboardingPathsResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val phaseCount: Int,
    val stepCount: Int,
    val finishedStepCount: Int,
    val blueprintId: UUID? = null,
)
