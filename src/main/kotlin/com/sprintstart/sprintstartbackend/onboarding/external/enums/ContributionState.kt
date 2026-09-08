package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Where one contribution got to.
 *
 * [IN_FLIGHT] and [ABANDONED] are deliberately separate rather than folded into "not accepted": a
 * contribution still in flight is waiting on somebody, which is the failure onboarding
 * instrumentation exists to catch, while an abandoned one is finished and waiting on nobody.
 * Counting the two together would inflate every "waiting on a response" number.
 */
enum class ContributionState {
    /** Submitted and awaiting the team's verdict. */
    IN_FLIGHT,

    /** Accepted through the team's normal quality bar. */
    ACCEPTED,

    /** Closed without being accepted. Done, and waiting on no one. */
    ABANDONED,
}
