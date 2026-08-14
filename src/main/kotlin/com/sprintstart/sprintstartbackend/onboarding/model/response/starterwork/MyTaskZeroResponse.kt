package com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork

import java.time.Instant

/**
 * A hire's Task 0 on one project, as they see it on their first-week surface.
 *
 * Available from day one — there is no environment-readiness precondition. Every combination is a
 * real, handled state, none an error:
 * - a [task] → the auto-assigned first task, with the PR loop to walk;
 * - [noneAvailable] with no task → no PM has flagged a Task 0 yet. A hire whose first day would
 *   otherwise end with "pick something" instead sees "nothing to assign right now", which is a gap
 *   for the PM to fill, not a failure of the hire.
 *
 * [loopProven] is derived — true once the hire has merged any pull request — and writes nothing to
 * the competency ledger: Task 0 proves the loop, not a competency.
 */
data class MyTaskZeroResponse(
    val task: StarterWorkTaskProposalResponse?,
    val assignedAt: Instant?,
    val noneAvailable: Boolean,
    val loopProven: Boolean,
)
