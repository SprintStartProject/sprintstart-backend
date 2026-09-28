package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Who put one card behind another.
 *
 * Two different claims used to be written into the same list, and stored as bare ids they were
 * indistinguishable. "The team says you cannot touch deploys before you have read the runbook" is a
 * rule about the work; "I want to do these three in this order" is one person's plan for their
 * afternoon. Without this, a hire's own picker could quietly clear a rule a PM had written into a
 * blueprint, and nobody — not the PM, not the buddy — would ever know it had gone.
 */
enum class CardDependencySource {
    /** From a card blueprint. The PM's, and the hire may not take it off. */
    TEAM,

    /** From a generated path. Named on the card so it does not look like the hire's own doing, but
     * still theirs to clear: the buddy is an assistant, not an authority. */
    BUDDY,

    /** The hire's own, and the only kind their own controls write. */
    HIRE,
}
