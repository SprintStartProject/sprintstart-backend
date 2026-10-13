package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Who made a change to a card.
 *
 * Not [BoardCardOwner], which answers who may change a card. This answers who *did*, and the two
 * part ways the moment the buddy edits a note the hire wrote: the card is still the hire's, and the
 * words on it are now the buddy's. A board that cannot say which is a board where the hire finds
 * different text under their own name and has no way to tell how it got there.
 */
enum class BoardActor {
    /** The hire, on their own board. */
    HIRE,

    /** The buddy, acting for the hire — always behind a confirm the hire pressed. */
    BUDDY,
}
