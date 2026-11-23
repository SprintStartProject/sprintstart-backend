package com.sprintstart.sprintstartbackend.path.model.response.path

import java.time.Instant
import java.util.UUID

data class CreateOnboardingPathResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val blueprintId: UUID? = null,
)
