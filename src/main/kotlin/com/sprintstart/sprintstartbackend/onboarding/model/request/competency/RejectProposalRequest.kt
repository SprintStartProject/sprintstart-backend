package com.sprintstart.sprintstartbackend.onboarding.model.request.competency

/**
 * Request body for rejecting a proposed competency or competency edge.
 *
 * @property reason Optional human-readable reason for the rejection.
 */
data class RejectProposalRequest(
    val reason: String? = null,
)
