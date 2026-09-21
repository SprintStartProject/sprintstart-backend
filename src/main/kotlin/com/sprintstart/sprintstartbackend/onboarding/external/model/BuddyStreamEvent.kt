package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One SSE chunk the backend emits to the browser on the buddy stream -- mirrors `sprintstart-backend`'s
 * own `chat` module's `AiStreamMessage` shape field-for-field, using the same
 * `sse_event` vocabulary `/chat` does (`tool_use`/`token`/`citation`/`action_proposal`/`done`/`error`).
 * Kept as its
 * own type in this module rather than reused from `chat` (that module's `AiStreamMessage` is
 * `internal` and this module owns its own AI-contract DTOs, same convention every other
 * `OnboardingAiClient` method follows).
 */
@Serializable
data class BuddyStreamEvent(
    val type: String,
    val content: String? = null,
    val name: String? = null,
    val kind: String? = null,
    @SerialName("artifact_id") val artifactId: String? = null,
    val filename: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("start_line") val startLine: Int? = null,
    @SerialName("start_page") val startPage: Int? = null,
    val message: String? = null,
    // Set only on an `action_proposal` event: the buddy is offering to *do* something, gated on the
    // hire's confirmation. [action] is the tool name the confirm endpoint expects, [label] the
    // button text, [question] the composed text carried through for the flag-to-PM action.
    // [taskId] is the confirm payload of the goal-claim action — the client echoes it back
    // verbatim, so the concrete target of an action is the one the buddy proposed, never one the
    // client picked.
    val action: String? = null,
    val label: String? = null,
    val question: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    /** Attestation confirm payload: what work, and who the hire is asking to confirm it. */
    val title: String? = null,
    @SerialName("attester_id") val attesterId: String? = null,
    /** `set_github_login` confirm payload: the username the buddy offered to save. */
    @SerialName("github_login") val githubLogin: String? = null,
    /** `record_assessment` confirm payload: which competency, and the level in words. */
    @SerialName("competency_key") val competencyKey: String? = null,
    val level: String? = null,
    /**
     * `place_checklist` confirm payload: the list the mentor offered to keep, as it was offered.
     *
     * Echoed back on confirm like every other payload here, and for a sharper reason: these lines
     * are content rather than a target id, so re-deriving them at confirm time would mean writing
     * a card the hire never read. What they saw is what gets kept.
     */
    @SerialName("checklist_title") val checklistTitle: String? = null,
    @SerialName("checklist_items") val checklistItems: List<String>? = null,
    /** `amend_checklist`: which card of theirs the lines would be added to. */
    @SerialName("card_id") val cardId: String? = null,
    /** `place_note` confirm payload: the note's text, as the hire will read it on the offer. */
    @SerialName("note_text") val noteText: String? = null,
    /** `reword_checklist_item`: the line as it reads now, and as it would read. Both, so the
     *  hire confirms a change they can see rather than one they would have to go looking for. */
    @SerialName("line_before") val lineBefore: String? = null,
    @SerialName("line_after") val lineAfter: String? = null,
)
