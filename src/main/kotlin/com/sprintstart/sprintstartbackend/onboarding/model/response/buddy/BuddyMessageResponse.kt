package com.sprintstart.sprintstartbackend.onboarding.model.response.buddy

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import java.time.Instant
import java.util.UUID

data class BuddyMessageResponse(
    val id: UUID,
    val role: BuddyMessageRole,
    val content: String,
    val createdAt: Instant,
)
