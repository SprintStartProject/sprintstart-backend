package com.sprintstart.sprintstartbackend.onboarding.model.response.verification

import java.util.UUID

/**
 * Grading result of a submitted verification attempt.
 *
 * There is no per-user status to report: a module is shared, which is exactly the point. Passing
 * writes the ledger, and the node's state is derived from there.
 */
data class SubmitVerificationAttemptResponse(
    val attemptId: UUID,
    val moduleId: UUID,
    val passed: Boolean,
    val score: Double,
    val feedback: String,
    // Escalating hint for the next attempt; null when passed.
    val hint: String? = null,
    val attemptNo: Int,
)
