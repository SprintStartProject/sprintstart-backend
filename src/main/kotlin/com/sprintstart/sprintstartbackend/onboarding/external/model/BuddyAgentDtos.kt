package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTOs for the AI service's `POST /api/v1/onboarding/buddy/agent` endpoint — the tool-using buddy.
 *
 * The AI service is a stateless reasoner: the backend carries the running [messages] list between
 * calls and owns tool execution for tools only it can run ([backendTools]). ``search_docs`` is run
 * AI-side; a backend tool comes back in [BuddyAgentResponse.pendingToolCalls] for the backend to
 * execute and feed back as a `tool` message.
 */
@Serializable
data class BuddyToolCallDto(
    val id: String,
    val name: String,
    // Opaque to the backend for a no-argument tool; round-tripped verbatim when carried back.
    val arguments: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class BuddyAgentMessageDto(
    // One of system | user | assistant | tool.
    val role: String,
    val content: String = "",
    @SerialName("tool_calls") val toolCalls: List<BuddyToolCallDto> = emptyList(),
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class BuddyToolSpecDto(
    val name: String,
    val description: String,
    // JSON-schema of the tool's arguments.
    val parameters: JsonObject,
)

@Serializable
data class BuddyAgentRequest(
    val messages: List<BuddyAgentMessageDto>,
    @SerialName("backend_tools") val backendTools: List<BuddyToolSpecDto> = emptyList(),
    /**
     * The session's running summary of everything older than [messages] — the conversation the
     * window no longer carries. Sent on the first hop of a turn; after that the AI has folded it
     * into the running [messages] list it returns, so it round-trips on its own.
     */
    @SerialName("prior_summary") val priorSummary: String? = null,
    /**
     * What one unit of this hire's accepted work is called, for the mentor's persona.
     *
     * Three structured fields rather than persona prose: the AI renders them into a fixed sentence
     * skeleton, so however many tracks exist the mentor stays one voice. Defaults to the
     * engineering wording on both sides, so an older AI service — or a hire on no track — reads
     * exactly as it did before tracks existed.
     */
    @SerialName("vocabulary") val vocabulary: BuddyVocabularyDto = BuddyVocabularyDto(),
    /**
     * The projects this hire is on, scoping what `search_docs` may retrieve.
     *
     * Several is ordinary — somebody onboarding on two projects should find material from both,
     * and from neither of anybody else's. Empty searches the whole corpus, which is right only on
     * a deployment serving one project; material belonging to no project stays searchable either
     * way, so nothing ingested before projects were carried disappears.
     */
    @SerialName("project_ids") val projectIds: List<String> = emptyList(),
)

/**
 * The nouns and verb one track's accepted work is described with.
 *
 * [contributionNoun] is bare ("change", "ceremony") because it is always rendered next to
 * [contributionVerbPast]; baking the verb into the noun produces "merged merged change" the moment
 * a sentence needs both.
 */
@Serializable
data class BuddyVocabularyDto(
    @SerialName("contribution_noun") val contributionNoun: String = "change",
    @SerialName("contribution_noun_plural") val contributionNounPlural: String = "changes",
    @SerialName("contribution_verb_past") val contributionVerbPast: String = "merged",
)

@Serializable
data class BuddyCitationDto(
    @SerialName("artifact_id") val artifactId: String? = null,
    @SerialName("start_line") val startLine: Int? = null,
    @SerialName("start_page") val startPage: Int? = null,
)

/**
 * DTOs for the AI service's `POST /api/v1/onboarding/buddy/open` endpoint.
 *
 * Opening a visit folds the previous visit ([recent]) into the mentor's durable [memory] and
 * returns a warm, proactive greeting grounded in [state] (a plain-text snapshot
 * of the hire's pull requests, tasks and competencies). Stateless like every AI endpoint: the
 * backend supplies the prior memory and persists the returned one.
 */
@Serializable
data class BuddyOpenRequest(
    val memory: String? = null,
    val recent: List<BuddyAgentMessageDto> = emptyList(),
    val state: String = "",
)

/**
 * Asks the AI service to fold [folded] into the mentor's durable memory note.
 *
 * ⚠️ **Nobody is waiting on this call, which is the whole reason it is one.** Folding on the agent
 * hop instead puts the fold *before* the agent loop, so once a visit's active window outgrows
 * `BuddyService.WINDOW`, every further turn pays an extra serialized model call to compress one
 * exchange ahead of the answer the hire is waiting for. Keep it off the answering path.
 *
 * The cursor is this side's: [folded] is exactly the slice to advance past once the fold succeeds.
 */
@Serializable
data class BuddyCompactRequest(
    @SerialName("prior_summary") val priorSummary: String? = null,
    val folded: List<BuddyAgentMessageDto> = emptyList(),
)

/**
 * The rewritten memory note.
 *
 * ⚠️ **A failure arrives as a non-2xx, never as this carrying the note unchanged** — the caller must
 * be able to tell *nothing folded* from *folded to the same words*, because advancing the cursor
 * past messages nothing summarized is the one way this design loses a transcript.
 */
@Serializable
data class BuddyCompactResponse(
    val memory: String,
)

@Serializable
data class BuddyOpenActionDto(
    val label: String,
    val question: String,
)

/**
 * One chunk of the AI service's streamed buddy open.
 *
 * ⚠️ **The greeting is streamed and the memory note is not**, and that asymmetry is the entire
 * point. The note is up to 200 words the hire never sees, and while it was generated *first* — as
 * the leading field of a strict-JSON reply — opening the buddy meant waiting for it before the
 * first word addressed to the hire even existed. Greeting-first plus streaming removes that wait
 * without a second model call.
 *
 * A `token` carries [content]; the terminal `done` carries [greeting], [memory] and any [action],
 * which is what the backend persists. [greeting] is byte-identical to the concatenated tokens, so
 * the message a hire watches arrive is the one stored for them to reload.
 */
@Serializable
data class BuddyOpenStreamEvent(
    val type: String,
    val content: String? = null,
    val greeting: String? = null,
    val memory: String? = null,
    val action: BuddyOpenActionDto? = null,
    val message: String? = null,
) {
    companion object {
        const val TOKEN = "token"
        const val DONE = "done"
    }
}

@Serializable
data class BuddyAgentResponse(
    // True when [text] is the answer; false when [pendingToolCalls] must be run first.
    val final: Boolean,
    val text: String = "",
    // The full running conversation to carry back verbatim on a resume.
    val messages: List<BuddyAgentMessageDto> = emptyList(),
    @SerialName("pending_tool_calls") val pendingToolCalls: List<BuddyToolCallDto> = emptyList(),
    val citations: List<BuddyCitationDto> = emptyList(),
)
