package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * An action the buddy agent may *propose* and, on the hire's explicit confirmation, perform.
 *
 * Each wraps an existing `/me/...` operation with the same caller-scoping. The [toolName] is the
 * name the AI reasoner calls; the [label] is the button the hire confirms. A proposal never mutates
 * — only the confirm round-trip does — so the two are deliberately the same catalog, read at both
 * ends, and can never drift apart.
 */
enum class BuddyActionType(
    val toolName: String,
    val label: String,
) {
    FLAG_TO_PM("flag_to_pm", "Flag this to your PM"),
    CLAIM_TASK_ZERO("claim_task_zero", "Start Task 0"),
    OPEN_ORIENTATION("open_orientation", "Open the task packet"),
    CLAIM_GOAL("claim_goal", "Work toward this task"),
    REQUEST_ATTESTATION("request_attestation", "Ask them to confirm this"),

    /**
     * Records the GitHub account the hire says their work comes from.
     *
     * Not project-scoped, unlike every action above it. A GitHub login is a fact about a
     * *person* — the same reason `GET /me/arrival` is not project-scoped — so this one is offered
     * and performed before the project gate. A hire on no project yet is exactly the hire most
     * likely to be setting one.
     */
    SET_GITHUB_LOGIN("set_github_login", "Save this username"),

    /**
     * Records where a conversation placed the hire on one competency.
     *
     * Not project-scoped, for the same reason as [SET_GITHUB_LOGIN] and by the same rule
     * `MyCompetencyService` states: the ledger is global, because a skill somebody has does not
     * stop being true on their second project.
     *
     * Its [label] is the fallback rather than the button the hire usually sees — the proposal names
     * the competency and the level, because "Save this" over a judgement about somebody's own
     * skill is not something anybody should have to confirm blind.
     */
    RECORD_ASSESSMENT("record_assessment", "Save this placement"),

    /**
     * Keeps a list the mentor just wrote as a checklist card on the hire's board.
     *
     * **The one action whose payload is content the model wrote**, and the reason it is an action
     * rather than a board tool. Every other card is a request to show a known read; this one puts
     * the mentor's own sentences on a surface the hire treats as theirs, so the confirm button is
     * not a formality here — it is the whole safeguard. What lands is what they read in the reply
     * above it, and the card is theirs to edit or throw away the moment it exists.
     *
     * The board already had the same thing as a button under any reply holding a list
     * (`SaveReplyToBoard`). That still stands, and this is for the other half of the cases: a task
     * whose steps the mentor had to work out rather than copy, where nothing in the reply looks
     * like a markdown list and the hire would otherwise be left retyping it.
     */
    PLACE_CHECKLIST("place_checklist", "Keep this as a checklist"),

    /**
     * Adds lines to a checklist the hire already has.
     *
     * The half of [PLACE_CHECKLIST] that stops the board filling with near-duplicates: a hire who
     * finishes two steps and asks what comes next should get the answer on the card they are
     * already ticking, not on a second one beside it.
     *
     * Append-only, and that is enforced in `BoardService.appendChecklistItems` rather than asked of
     * the mentor. Their existing lines come back untouched — same words, same ids, same ticks — and
     * the confirm shows only what would be added, because a change you cannot see is one you cannot
     * agree to.
     */
    AMEND_CHECKLIST("amend_checklist", "Add these to the list"),

    /**
     * Keeps a link the mentor cited as a card.
     *
     * The lowest-risk of the authored actions: the address comes from material this project has
     * ingested rather than from the model's memory, so the one thing on the card that has to be
     * exactly right is the one thing it did not invent.
     */
    PLACE_LINK("place_link", "Keep this link"),

    /**
     * Keeps an explanation the mentor just gave as a note.
     *
     * The weakest of the four on its own — every reply already carries a "keep this answer" button
     * that needs no tool. What this adds is the mentor *offering* when it can tell the answer is
     * worth having tomorrow, rather than waiting to be asked.
     */
    PLACE_NOTE("place_note", "Keep this as a note"),

    /**
     * Ticks lines on a checklist the hire says they have finished.
     *
     * The hire's own statement about their own work, written to their own card, behind the same
     * confirm as everything else here — which is what makes it theirs rather than the mentor's
     * verdict. It is offered when they *say* they have done something, never concluded from the
     * conversation: a board that ticks itself because a model read something into a sentence is a
     * board nobody can trust the state of.
     *
     * Setting only. Un-ticking would be somebody else deciding the hire was wrong about their own
     * work, and the checkbox on the card is right there.
     */
    TICK_CHECKLIST_ITEMS("tick_checklist_items", "Tick these off"),

    /**
     * Rewrites one line of a checklist, when the hire asks for that line to be clearer.
     *
     * Deliberately its own action rather than something [AMEND_CHECKLIST] could also do. That one
     * is append-only *by construction*: it is never handed the existing lines, so there is no
     * version of adding a step in which a line quietly changes on the way. Rewording is a thing the
     * hire asks for, about a line they name, and the confirm shows both wordings — which is what
     * makes it an edit they can see rather than one they would have to go looking for.
     */
    REWORD_CHECKLIST_ITEM("reword_checklist_item", "Reword this line"),
    ;

    companion object {
        fun fromToolName(toolName: String): BuddyActionType? = entries.firstOrNull { it.toolName == toolName }
    }
}
