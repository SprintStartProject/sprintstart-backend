package com.sprintstart.sprintstartbackend.onboarding.model.response.buddy

import java.time.Instant
import java.util.UUID

data class GetSessionsResponse(
    val sessions: List<SessionResponse>,
)

data class SessionResponse(
    val id: UUID,
    val title: String,
    val userId: UUID,
    val projectId: UUID?,
    val createdAt: Instant,
)
