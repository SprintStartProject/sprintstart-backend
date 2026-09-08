package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Who a card belongs to, which is what decides who may change it.
 *
 * Two owners is the whole model: one hire and one asynchronous mentor. There is no third editor
 * and no simultaneous editing, so the board needs no collaboration machinery — last write wins,
 * scoped by ownership.
 */
enum class BoardCardOwner {
    /**
     * Placed for the hire rather than by them.
     *
     * The hire may dismiss it — and that dismissal is sticky, so the mentor will not put it back —
     * but they do not edit it, because its content is a live read rather than something written.
     */
    AI,

    /**
     * The hire put it there.
     *
     * The mentor never edits or removes one of these. A board the mentor can tidy is a board the
     * hire cannot trust to keep what they put on it.
     */
    HIRE,
}
