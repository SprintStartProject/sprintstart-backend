package com.sprintstart.sprintstartbackend.onboarding.model.response.buddy

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import java.time.Instant

data class BuddyMessageResponse(
    val role: BuddyMessageRole,
    val content: String,
    val createdAt: Instant,
)
