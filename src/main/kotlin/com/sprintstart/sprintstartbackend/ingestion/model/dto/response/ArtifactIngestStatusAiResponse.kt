package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One artifact's index state as the AI service reports it on `GET /api/v1/ingest/status`.
 *
 * Kept as raw strings on purpose: this is the AI wire format, and mapping onto the backend's own
 * [com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactAiIndexStatus] happens in the
 * service, so a status the AI adds later degrades to UNKNOWN instead of failing deserialization.
 *
 * @property artifactId The requested id, echoed verbatim.
 * @property status Lowercase AI status (indexed, processing, failed, deindexed, unknown).
 * @property updatedAt ISO timestamp of the last recorded change; null when the AI holds no record.
 * @property chunkCount Chunks recorded for the artifact; null when the AI holds no record.
 */
@Serializable
data class ArtifactIngestStatusAiItem(
    @SerialName("artifact_id")
    val artifactId: String,
    val status: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("chunk_count")
    val chunkCount: Int? = null,
)

/** AI response body of `GET /api/v1/ingest/status`: one item per distinct requested id. */
@Serializable
data class ArtifactIngestStatusAiResponse(
    val items: List<ArtifactIngestStatusAiItem>,
)
