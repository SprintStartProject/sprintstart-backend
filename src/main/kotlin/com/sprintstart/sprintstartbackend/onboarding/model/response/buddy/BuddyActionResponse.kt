package com.sprintstart.sprintstartbackend.onboarding.model.response.buddy

/**
 * The outcome of a confirmed buddy action, as a single line the buddy can relay in the thread.
 *
 * [ok] is true when the action changed something, false when it legibly could not (e.g. no eligible
 * Task 0). A false outcome is a handled state, not an error — [message] always carries a reason the
 * hire can read either way.
 */
data class BuddyActionResponse(
    val ok: Boolean,
    val message: String,
)
