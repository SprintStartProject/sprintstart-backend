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
    /**
     * The path node a path action is aimed at: [stepId] for `complete_step`, [questionId] for
     * `answer_question`, [phaseId] for `add_path_step`.
     *
     * Echoed back verbatim like every other payload here, and re-resolved server-side through the
     * caller's *own* path — so an id that belongs to somebody else's onboarding is not found rather
     * than acted on.
     */
    val stepId: UUID? = null,
    val questionId: UUID? = null,
    val phaseId: UUID? = null,
    /**
     * The checklist line `complete_task` would tick off.
     *
     * Its own field rather than [taskId], which already means a *starter-work* task for `claim_goal`.
     * Two different things called a task is confusing enough in the product without one wire field
     * standing for both.
     */
    val onboardingTaskId: UUID? = null,
    /**
     * The hire's answer to a knowledge question, in their own words, for `answer_question`.
     *
     * Matched to an option server-side for a multiple-choice question rather than being sent as an
     * option id, for the same reason `record_assessment` re-reads the level from its word: what is
     * recorded should be derived from what the hire was shown, not from something a client
     * substituted afterwards.
     */
    val answer: String? = null,
    /** What a step added by `add_path_step` is about, one or two sentences. */
    val description: String? = null,
)
