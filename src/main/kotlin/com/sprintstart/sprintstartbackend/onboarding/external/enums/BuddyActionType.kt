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
     * The three path actions: the mentor moving the hire along the curriculum their PM wrote.
     *
     * They are what turns the buddy from a second onboarding mechanism into the tutor for the first
     * one. One line decides how far that goes, and it is worth stating here rather than only in the
     * tool descriptions: **these touch the hire's own copy of the path, never the blueprint.** The
     * curriculum belongs to the PM; a mentor that could edit it is a mentor whose team stops
     * trusting it. Everything here is reversible on the hire's own page, which is what makes
     * proposing them reasonable at all.
     *
     * Their [label]s are fallbacks. Each proposal names the actual step, the actual answer or the
     * actual title, because "Confirm" over a change to somebody's onboarding is not something
     * anybody should have to click blind.
     */
    COMPLETE_STEP("complete_step", "Mark this step as done"),

    /**
     * Sends the hire's own answer to a knowledge question.
     *
     * The hire's words, never the mentor's. The mentor is not told which option is correct (see
     * `BuddyPathTools`), so it cannot answer for them even if it tried — and the button shows the
     * answer that will be sent, because an attempt is recorded whether it is right or not.
     */
    ANSWER_QUESTION("answer_question", "Send this answer"),

    /** Adds a step the conversation produced to a phase of the hire's own path. */
    ADD_PATH_STEP("add_path_step", "Add this step to your path"),
    ;

    companion object {
        fun fromToolName(toolName: String): BuddyActionType? = entries.firstOrNull { it.toolName == toolName }
    }
}
