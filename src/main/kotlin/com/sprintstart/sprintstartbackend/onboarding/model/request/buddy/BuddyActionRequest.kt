package com.sprintstart.sprintstartbackend.onboarding.model.request.buddy

import java.util.UUID

/**
 * A hire confirming a buddy-proposed action.
 *
 * [action] is the proposed action's tool name (see `BuddyActionType`). The project is **not**
 * carried here — it is re-resolved server-side from the caller, so a client can never confirm an
 * action against a project the buddy did not scope it to. The remaining fields are the per-action
 * confirm payloads the proposal carried, echoed back verbatim: [question] for flag-to-PM (the
 * text the buddy composed and showed the hire), [taskId] for claiming a suggested goal,
 * [moduleId] + [answer] for submitting a module check. All are ignored by the actions that don't
 * use them.
 */
data class BuddyActionRequest(
    val action: String,
    val question: String? = null,
    val taskId: UUID? = null,
    val moduleId: UUID? = null,
    val answer: String? = null,
    val title: String? = null,
    val attesterId: UUID? = null,
    /** The GitHub account to record, for `set_github_login`. */
    val githubLogin: String? = null,
)
