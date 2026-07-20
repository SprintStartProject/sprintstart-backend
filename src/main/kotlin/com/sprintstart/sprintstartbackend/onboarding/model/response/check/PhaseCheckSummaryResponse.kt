package com.sprintstart.sprintstartbackend.onboarding.model.response.check

import java.time.Instant
import java.util.UUID

/**
 * Compact knowledge check state of a phase, embedded into the user's path response.
 */
data class PhaseCheckSummaryResponse(
    val required: Boolean,
    val questionCount: Int,
    val passed: Boolean,
    val latestAttemptId: UUID?,
    val latestAttemptAt: Instant?,
)
