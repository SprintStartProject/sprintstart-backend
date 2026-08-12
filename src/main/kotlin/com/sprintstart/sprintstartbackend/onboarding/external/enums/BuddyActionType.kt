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
    SUBMIT_VERIFICATION("submit_verification", "Submit this answer"),
    REQUEST_ATTESTATION("request_attestation", "Ask them to confirm this"),

    /**
     * Records the GitHub account the hire says their work comes from.
     *
     * ⚠️ **Not project-scoped, unlike every action above it.** A GitHub login is a fact about a
     * *person* — the same reason `GET /me/arrival` is not project-scoped — so this one is offered
     * and performed before the project gate. A hire on no project yet is exactly the hire most
     * likely to be setting one.
     */
    SET_GITHUB_LOGIN("set_github_login", "Save this username"),
    ;

    companion object {
        fun fromToolName(toolName: String): BuddyActionType? = entries.firstOrNull { it.toolName == toolName }
    }
}
