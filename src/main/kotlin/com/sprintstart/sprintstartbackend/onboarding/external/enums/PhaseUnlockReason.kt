package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Explains why an onboarding phase is still locked for the user.
 */
enum class PhaseUnlockReason {
    PREVIOUS_PHASE_INCOMPLETE,
    PREVIOUS_PHASE_CHECK_NOT_PASSED,
}
