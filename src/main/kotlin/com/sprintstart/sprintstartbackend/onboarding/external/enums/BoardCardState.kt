package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Whether a card is on the board.
 *
 * A dismissed card keeps its row rather than being deleted, and that is the entire mechanism behind
 * sticky removal: the board can go on ensuring the state-relevant cards exist on every load without
 * ever re-adding one the hire has already said no to. Deleting the row would make dismissal a
 * gesture the mentor undoes on the next visit.
 */
enum class BoardCardState {
    ACTIVE,
    DISMISSED,
}
