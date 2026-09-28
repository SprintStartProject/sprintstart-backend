package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The step of the path to a first pull request an orientation section belongs to.
 *
 * Segmentation is by *process*, not by topic: a hire on day three opens "check locally" and does not
 * re-read setup. Mirrors the AI service's `OrientationStep`; a value outside this set is dropped on
 * persist rather than stored, the same rule the AI applies on its side.
 *
 * The declaration order is the render order, so a packet is always readable front to back even if
 * the AI returned its sections shuffled.
 */
enum class OrientationStep {
    SET_UP,
    FIND_THE_CODE,
    MAKE_THE_CHANGE,
    CHECK_LOCALLY,
    OPEN_THE_PR,
}
