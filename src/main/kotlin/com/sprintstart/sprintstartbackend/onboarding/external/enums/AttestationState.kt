package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Where a request for someone to confirm a hire's work has got to.
 *
 * There is no rejected state on purpose. An attester who is not satisfied sends the work *back*
 * with a reason, which returns it to [REQUESTED] and counts as rework -- the same shape as a pull
 * request with changes requested. A terminal "no" would end the hire's attempt at the work rather
 * than the attempt at this submission, which is not what a reviewer saying "not yet" means.
 */
enum class AttestationState {
    /** Waiting on the attester. Either never answered, or sent back and resubmitted. */
    REQUESTED,

    /** Confirmed: the work happened and met the bar. The contribution's acceptance moment. */
    ACCEPTED,

    /** The hire withdrew it. Finished, and waiting on nobody. */
    WITHDRAWN,
}
