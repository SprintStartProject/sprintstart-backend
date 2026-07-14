package com.sprintstart.sprintstartbackend.artifacts.model.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One SSE chunk from the AI service's streaming artifact-summary endpoint.
 *
 * Mirrors chat's `AiStreamMessage`: [type] distinguishes the chunk shape and only the
 * fields relevant to that type are populated; the rest stay null.
 *
 * Event shapes:
 * - `stage`: [name] + [detail] -- progress while notes are gathered from the source (not
 *   itself streamed -- internal working notes, not the final summary).
 * - `token`: [content] -- a fragment of the summary text.
 * - `citation`: [artifactId] + [filename] + [sourceUrl] -- a source cited in the summary.
 *   Unlike chat citations, the AI service already owns and sends filename/sourceUrl here,
 *   so no backend-side enrichment is needed.
 * - `done`: terminal marker, no extra fields.
 * - `error`: [message] -- the stream failed.
 */
@Serializable
data class AiArtifactSummaryStreamMessage(
    val type: String,
    val content: String? = null,
    val name: String? = null,
    val detail: String? = null,
    @SerialName("artifact_id")
    val artifactId: String? = null,
    val filename: String? = null,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    val message: String? = null,
)
