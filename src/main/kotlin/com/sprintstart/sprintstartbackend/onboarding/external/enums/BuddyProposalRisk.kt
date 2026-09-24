package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * How much a confirmed team-mode action changes, which decides how its confirm card is drawn.
 *
 * Declared by the action itself, never by the model: a model that calls a deletion "standard" must
 * not get a one-click card for it.
 */
enum class BuddyProposalRisk {
    /** Reversible edits: a plain confirm. */
    STANDARD,

    /** Removes something or someone: the card states the consequence in words. */
    DESTRUCTIVE,

    /** Touches many things or runs long: the card names the scope. */
    BULK,
}
