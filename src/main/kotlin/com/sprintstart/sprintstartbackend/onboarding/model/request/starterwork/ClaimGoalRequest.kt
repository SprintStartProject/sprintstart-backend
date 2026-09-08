package com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork

import java.util.UUID

/**
 * A hire claiming a live starter-work task as the destination of their path.
 *
 * Identified by the *proposal* id, because that is what `GET /me/matches` hands the client. The
 * server resolves it to the CONTRIBUTION competency key through the same deterministic derivation
 * approval used -- the client never has to know that mapping, and cannot get it wrong.
 */
data class ClaimGoalRequest(
    val taskId: UUID,
)
