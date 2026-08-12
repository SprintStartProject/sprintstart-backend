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
    // [taskId]/[moduleId]/[answer] are the confirm payloads of the goal-claim and verification
    // actions — the client echoes them back verbatim, so the concrete target of an action is the
    // one the buddy proposed, never one the client picked.
    val action: String? = null,
    val label: String? = null,
    val question: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("module_id") val moduleId: String? = null,
    val answer: String? = null,
    /** Attestation confirm payload: what work, and who the hire is asking to confirm it. */
    val title: String? = null,
    @SerialName("attester_id") val attesterId: String? = null,
    /** `set_github_login` confirm payload: the username the buddy offered to save. */
    @SerialName("github_login") val githubLogin: String? = null,
)
