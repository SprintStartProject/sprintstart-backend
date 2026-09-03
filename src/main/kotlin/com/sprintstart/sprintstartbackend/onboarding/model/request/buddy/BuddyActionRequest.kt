package com.sprintstart.sprintstartbackend.onboarding.model.request.buddy

import java.util.UUID

/**
 * A hire confirming a buddy-proposed action.
 *
 * [action] is the proposed action's tool name (see `BuddyActionType`). The project is not
 * carried here — it is re-resolved server-side from the caller, so a client can never confirm an
 * action against a project the buddy did not scope it to. The remaining fields are the per-action
 * confirm payloads the proposal carried, echoed back verbatim: [question] for flag-to-PM (the
 * text the buddy composed and showed the hire), [taskId] for claiming a suggested goal. All are
 * ignored by the actions that don't use them.
 */
data class BuddyActionRequest(
    val action: String,
    val question: String? = null,
    val taskId: UUID? = null,
    val title: String? = null,
    val attesterId: UUID? = null,
    /** The GitHub account to record, for `set_github_login`. */
    val githubLogin: String? = null,
    /**
     * Which competency a conversational placement is about, and where it put the hire, for
     * `record_assessment`. The level is the word ("beginner".."expert"), never a rank: the scale is
     * re-read server-side, so a client cannot confirm a level the scale does not have.
     */
    val competencyKey: String? = null,
    val level: String? = null,
)
