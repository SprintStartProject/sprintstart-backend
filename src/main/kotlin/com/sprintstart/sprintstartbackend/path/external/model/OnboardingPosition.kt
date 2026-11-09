package com.sprintstart.sprintstartbackend.path.external.model

/**
 * Where somebody stands in their onboarding, in the words a reader can put on a screen.
 *
 * Titles rather than entities: every consumer so far wants to *say* where somebody is, and handing
 * out the entities would let a caller reach through this into the path and start writing to it.
 */
data class OnboardingPosition(
    val currentPhase: String?,
    val currentStep: String?,
    val progressPercentage: Double,
)
