package com.sprintstart.sprintstartbackend.chat.models.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Specifies the format of streamed messages incoming from the AI repo.
 *
 * The AI service multiplexes several event shapes over a single SSE stream, all
 * distinguished by [type]. Only the fields relevant to a given [type] are populated;
 * the rest stay `null` and are omitted on (de)serialization. The backend acts as a
 * transparent passthrough, so the wire field names mirror the AI service contract
 * exactly (snake_case where applicable) and are forwarded unchanged to the client.
 *
 * Event shapes:
 * - `tool_use`: [name] + [kind] — an agent/tool the orchestrator invoked.
 * - `token`: [content] — a fragment of the generated answer.
 * - `citation`: [chunkId] + [filename] + [sectionPath] — a source used in the answer.
 * - `done`: terminal marker, no extra fields.
 * - `error`: [message] — the stream failed.
 *
 * @property type The type of stream message this is (e.g. 'token', 'tool_use', 'citation', 'done', 'error').
 * @property content For `token` events: a single token or short fragment of the answer.
 * @property name For `tool_use` events: the name of the invoked capability (e.g. 'retrieve').
 * @property kind For `tool_use` events: whether the capability is a leaf 'tool' or a sub-'agent'.
 * @property chunkId For `citation` events: the id of the source chunk.
 * @property filename For `citation` events: the name of the file the citation refers to.
 * @property sectionPath For `citation` events: the heading breadcrumb (e.g. 'Retro > Blockers'), or null.
 * @property message For `error` events: the error description.
 */
@Serializable
data class AiStreamMessage(
    val type: String,
    val content: String? = null,
    val name: String? = null,
    val kind: String? = null,
    @SerialName("chunk_id") val chunkId: String? = null,
    val filename: String? = null,
    @SerialName("section_path") val sectionPath: String? = null,
    val message: String? = null,
)
