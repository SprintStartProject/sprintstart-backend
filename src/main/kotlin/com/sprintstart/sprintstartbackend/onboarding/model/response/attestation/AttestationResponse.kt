package com.sprintstart.sprintstartbackend.onboarding.model.response.attestation

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import java.time.Instant
import java.util.UUID

/**
 * One request for somebody to confirm a hire's work.
 *
 * [returnedCount] is shown rather than hidden: work that took three passes is not the same as work
 * that took none, and autonomy reads exactly this number.
 */
data class AttestationResponse(
    val id: UUID,
    val hireId: UUID,
    val hireName: String?,
    val projectId: UUID,
    val title: String,
    val evidenceUrl: String?,
    val attesterId: UUID,
    val attesterName: String?,
    val state: AttestationState,
    val requestedAt: Instant,
    val firstResponseAt: Instant?,
    val acceptedAt: Instant?,
    val returnedCount: Int,
    val returnReason: String?,
)
