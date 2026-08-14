package com.sprintstart.sprintstartbackend.onboarding.model.request

import java.util.UUID

/** A hire asking a named colleague to confirm a piece of work. */
data class RequestAttestationRequest(
    val projectId: UUID,
    val title: String,
    val evidenceUrl: String? = null,
    val attesterId: UUID,
)

/** An attester sending work back, with what needs to change. */
data class SendBackAttestationRequest(
    val reason: String,
)
