package com.sprintstart.sprintstartbackend.onboarding.external.event

import java.util.UUID

data class SkipRequestApprovedEvent(
    val stepId: UUID,
    val skipReason: String,
)
