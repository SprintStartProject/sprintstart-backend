package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Where a stored team-mode action proposal stands.
 *
 * Only [PROPOSED] can move, and only once: to [CONFIRMING] when the manager confirms, to [DISMISSED]
 * when they decline, or to [EXPIRED] when it outlived its window. [CONFIRMING] exists so two confirms
 * racing each other cannot both run the action — the transition out of [PROPOSED] is a single
 * conditional update, and only the request that wins it performs anything.
 */
enum class BuddyProposalStatus {
    PROPOSED,
    CONFIRMING,
    CONFIRMED,
    DISMISSED,
    EXPIRED,
    FAILED,
}
