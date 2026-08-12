package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtifactAiIngestResponse(
    @SerialName("artifact_id")
    val artifactId: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
    /**
     * Per-artifact outcome (`completed` / `failed`).
     *
     * The AI service never fails a whole batch -- it catches per artifact and reports the outcome
     * here. Before this field was read, a `200` was treated as "all indexed", so artifacts the AI
     * service had explicitly reported as failed were silently recorded as synced.
     */
    val status: String = "completed",
)
