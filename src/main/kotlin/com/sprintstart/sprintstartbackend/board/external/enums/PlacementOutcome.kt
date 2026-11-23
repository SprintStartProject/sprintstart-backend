package com.sprintstart.sprintstartbackend.board.external.enums

/**
 * What placing a card did.
 *
 * Every outcome is reported rather than collapsed into a boolean, because the mentor has to say
 * something afterwards and "I've put that on your board" is only true for one of them. A buddy
 * that cannot tell a refusal from a success will claim the success.
 */
enum class PlacementOutcome {
    PLACED,
    ALREADY_THERE,

    /** The hire took this card off their board before; it is not going back. */
    DISMISSED_BY_HIRE,

    NOT_A_MEMBER,

    /** A kind with [BoardCardKind.takesSubject] was asked for with no subject at all. */
    NEEDS_A_SUBJECT,

    /** A [BoardCardKind.PATH_STEP]'s subject named no real step of this hire's path. */
    NO_SUCH_STEP,
}
