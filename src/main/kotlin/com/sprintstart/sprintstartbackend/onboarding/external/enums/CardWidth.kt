package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * How much of the board's width one card takes.
 *
 * Three snapped widths rather than a pixel size: a board of cards at arbitrary sizes is not
 * personalised, it is ragged. They are spans on the client's own grid — one, two or four of its four
 * columns — which is what lets "narrower" exist at all.
 *
 * There is deliberately no height. A card is as tall as what is on it: a checklist grows as lines
 * are added and shrinks as they are ticked off. A height set by hand promises control over
 * something the content already answers correctly.
 */
enum class CardWidth {
    NARROW,
    NORMAL,
    WIDE,
}
