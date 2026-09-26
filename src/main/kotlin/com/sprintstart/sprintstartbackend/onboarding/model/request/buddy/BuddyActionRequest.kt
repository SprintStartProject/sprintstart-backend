package com.sprintstart.sprintstartbackend.onboarding.model.request.buddy

import java.util.UUID

/**
 * A hire confirming a buddy-proposed action.
 *
 * [action] is the proposed action's tool name (see `BuddyActionType`). The project is not
 * carried here — it is re-resolved server-side from the caller, so a client can never confirm an
 * action against a project the buddy did not scope it to. The remaining fields are the per-action
 * confirm payloads the proposal carried, echoed back verbatim — for example [question] for
 * flag-to-PM (the text the buddy composed and showed the hire) or [taskId] for claiming a
 * suggested goal; each field's own doc names the action it belongs to. All are ignored by the
 * actions that don't use them.
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
     * The path node a path action is aimed at: [stepId] for `complete_step` and `request_skip`,
     * [questionId] for `answer_question`, [phaseId] for `add_path_step`.
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
    /** The reason `request_skip` sends to the PM, in the words the hire confirmed. */
    val reason: String? = null,
    /**
     * Where `add_path_step` puts the new step in its phase's graph: the items it waits on, and the
     * items that will wait on it instead. Re-checked against the caller's own path at confirm time.
     */
    val waitsOnIds: List<UUID> = emptyList(),
    val unlocksIds: List<UUID> = emptyList(),
    /**
     * The checklist to keep, for `place_checklist` — echoed back exactly as it was proposed.
     *
     * Capped server-side rather than trusted: this is the one action whose payload is free text
     * from the client, so length and count are re-checked at confirm time.
     */
    val checklistTitle: String? = null,
    val checklistItems: List<String>? = null,
    /** `amend_checklist`: the card the lines go on. Re-checked as theirs before anything is written. */
    val cardId: UUID? = null,
    /** `place_note` payload: the note's text. */
    val noteText: String? = null,
    /** `reword_checklist_item`: which line, and what it should say instead. */
    val lineBefore: String? = null,
    val lineAfter: String? = null,
)
