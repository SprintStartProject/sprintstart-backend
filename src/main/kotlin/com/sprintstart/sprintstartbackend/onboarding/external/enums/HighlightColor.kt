package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The colour of one of the hire's highlights.
 *
 * Four, and deliberately not more: a highlighter with twelve colours is a colour picker, and a
 * colour picker turns "mark this" into a decision — the friction that stops people marking anything.
 *
 * Stored as a name rather than as a hex value, so a board marked up in the light theme is legible in
 * the dark one. They mean nothing on their own, which is the point: the hire assigns the meaning.
 */
enum class HighlightColor {
    YELLOW,
    GREEN,
    BLUE,
    PINK,
}
