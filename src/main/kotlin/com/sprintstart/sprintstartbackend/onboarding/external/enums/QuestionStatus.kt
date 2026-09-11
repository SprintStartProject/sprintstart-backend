package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Availability of a knowledge-check question for the user, mirroring the derived
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep] statuses.
 */
enum class QuestionStatus {
    /** A blocker (the phase or another node) is not complete yet. */
    LOCKED,

    /** Not answered yet; ready to be attempted. */
    OPEN,

    /** Answered before, never correctly; ready for another try. */
    RETRY,

    /** Answered correctly at least once. */
    PASSED,
}
