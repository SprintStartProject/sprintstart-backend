package com.sprintstart.sprintstartbackend.assistant.external

import java.util.UUID

/** The result of proposing an action: what to tell the AI, and the proposal to show the hire (if any). */
data class HireActionOutcome(
    val toolResult: String,
    val proposal: HireActionProposal?,
)

/** A proposed action for the hire to confirm — carried on the stream as an `action_proposal` event. */
data class HireActionProposal(
    val action: String,
    val label: String,
    val question: String?,
    /**
     * The goal-claim target. This and the confirm payloads below are echoed back verbatim by the client,
     * so a confirmed action targets what the buddy proposed.
     */
    val taskId: UUID? = null,
    val title: String? = null,
    val attesterId: String? = null,
    val githubLogin: String? = null,
    /** `record_assessment` confirm payload: which competency, and the level in words. */
    val competencyKey: String? = null,
    val level: String? = null,
    /**
     * Path-action confirm payloads: the node of the hire's own path the action names, the answer
     * `answer_question` would send in the hire's own words, and the description of a step
     * `add_path_step` would add.
     */
    val stepId: UUID? = null,
    val questionId: UUID? = null,
    val phaseId: UUID? = null,
    /** The checklist line `complete_task` would tick off. See the request DTO for why not [taskId]. */
    val onboardingTaskId: UUID? = null,
    val answer: String? = null,
    /** The options a multiple-choice `answer_question` [answer] stands for. See the request DTO. */
    val optionIds: List<UUID> = emptyList(),
    val description: String? = null,
    /** The reason `request_skip` would send to the PM. */
    val reason: String? = null,
    /** Where `add_path_step` would put the step in its phase's graph. */
    val waitsOnIds: List<UUID> = emptyList(),
    val unlocksIds: List<UUID> = emptyList(),
    /** `place_checklist` confirm payload: the list as the hire will read it before confirming. */
    val checklistTitle: String? = null,
    val checklistItems: List<String>? = null,
    /** `amend_checklist`: the card being added to, and only the lines being added. */
    val cardId: UUID? = null,
    /** `place_note` confirm payload. */
    val noteText: String? = null,
    /** `reword_checklist_item`: the line as it reads now, and as it would read. */
    val lineBefore: String? = null,
    val lineAfter: String? = null,
    /** `place_link` / `edit_link`: where the link points, and what it is called. */
    val linkUrl: String? = null,
    val linkLabel: String? = null,
    /** `dismiss_cards` / `reorder_cards`: the cards, in order, and their names for display. */
    val cardIds: List<UUID>? = null,
    val cardNames: List<String>? = null,
    /**
     * `edit_note` / `edit_link` / `edit_checklist`: a fingerprint of the card's words as the
     * proposal read them, echoed back so a confirm can tell the card changed in between.
     */
    val basedOn: String? = null,
    /** The board edits: what confirming would change, as one sentence the hire can read. */
    val preview: String? = null,
)
