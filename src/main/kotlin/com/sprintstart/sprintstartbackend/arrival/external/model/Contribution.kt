package com.sprintstart.sprintstartbackend.arrival.external.model

import com.sprintstart.sprintstartbackend.arrival.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.arrival.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor
import java.time.Instant
import java.util.UUID

/**
 * One unit of real work a hire produced, reduced to the moments onboarding measures.
 *
 * Not to be confused with `CompetencyKind.CONTRIBUTION`, which is a graph node kind: that is
 * the *goal* a hire is working toward, this is the *evidence* that they completed something. The
 * two meet only in that finishing the former produces the latter.
 *
 * [firstResponseAt] null means nobody has answered yet — a finding, not missing data.
 * [returnedCount] is kept beside acceptance because acceptance alone cannot tell clean work from
 * work sent back three times.
 *
 * Invariant: [state] `== ACCEPTED` implies [acceptedAt] is non-null, and vice versa. It is
 * established in the mappers that build these, which are the only way one is constructed.
 */
data class Contribution(
    /** The evidence this rests on: an ingested artifact today, an attestation row later. */
    val evidenceRef: UUID,
    val kind: ContributionEvidenceKind,
    val rigor: Rigor,
    val state: ContributionState,
    val openedAt: Instant?,
    val firstResponseAt: Instant?,
    val acceptedAt: Instant?,
    val returnedCount: Int,
) {
    /** Accepted through the team's normal quality bar. The unit the metrics count. */
    val isAccepted: Boolean
        get() = state == ContributionState.ACCEPTED

    /** Submitted and still waiting on somebody else. */
    val isInFlight: Boolean
        get() = state == ContributionState.IN_FLIGHT
}
