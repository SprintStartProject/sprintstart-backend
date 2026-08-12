package com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork

/**
 * A PM's decision on whether a live starter-work task is suitable as a hire's Task 0.
 */
data class SetTaskZeroEligibilityRequest(
    val eligible: Boolean,
)
