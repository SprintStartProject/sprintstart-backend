package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-artifact outcome of one AI indexing batch.
 *
 * [status] is reported by the AI service for every artifact: a batch can partially fail (a parse
 * error, a mid-batch LLM outage) and still answer `200`, so an artifact that never made it into
 * the index is only visible here.
 *
 * @property status Either `completed` or `failed`; see [AI_SYNC_STATUS_FAILED].
 */
@Serializable
data class ArtifactAiIngestResponse(
    @SerialName("artifact_id")
    val artifactId: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
    val status: String = AI_SYNC_STATUS_COMPLETED,
)

/**
 * Per-artifact outcome of removing an artifact from the AI index.
 *
 * A failed deindex leaves the artifact retrievable in chat, which is why the AI service reports it
 * instead of swallowing it: reading the surrounding `200` as "revoked" would keep answering from
 * content that was deleted.
 */
@Serializable
data class ArtifactAiDeindexResponse(
    @SerialName("artifact_id")
    val artifactId: String,
    val status: String = AI_SYNC_STATUS_COMPLETED,
    @SerialName("error_message")
    val errorMessage: String? = null,
)

const val AI_SYNC_STATUS_COMPLETED = "completed"

const val AI_SYNC_STATUS_FAILED = "failed"
