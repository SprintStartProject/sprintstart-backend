package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * What the most recent change to a card was.
 *
 * Only the latest one is kept, because the question the board answers is "what just happened to
 * this card, and was it me" — not a history. A change the hire makes afterwards replaces the
 * buddy's, which is right: once they have touched the card, the buddy's edit is no longer the thing
 * to point at.
 */
enum class BoardCardChange {
    CREATED,

    /** The content changed — a note rewritten, a link retargeted, a checklist's lines edited. */
    EDITED,

    /** Only ticks changed. Its own kind because "ticked two off" and "rewrote your list" differ. */
    TICKED,

    DISMISSED,

    /** Its place in the board's order changed. */
    MOVED,
}
