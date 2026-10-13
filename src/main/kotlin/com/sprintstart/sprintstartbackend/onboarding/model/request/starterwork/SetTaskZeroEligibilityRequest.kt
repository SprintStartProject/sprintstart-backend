package com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork

/**
 * A PM's decision on whether a live starter-work task is a good first one for somebody ("Task 0").
 *
 * A label on the task, not an assignment: flagging one hands it to nobody, and leaving one
 * unflagged keeps it from nobody. Hires claim their own work from the whole live pool.
 */
data class SetTaskZeroEligibilityRequest(
    val eligible: Boolean,
)
