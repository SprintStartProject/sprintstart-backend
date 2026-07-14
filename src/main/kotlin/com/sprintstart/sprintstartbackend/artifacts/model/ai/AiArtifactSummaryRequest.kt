package com.sprintstart.sprintstartbackend.artifacts.model.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request sent to the AI service to summarize an artifact.
 *
 * The AI service sources the artifact's content itself from its ingestion index; this call carries
 * no source data, only generation parameters.
 */
@Serializable
data class AiArtifactSummaryRequest(
    @SerialName("previous_artifact_id")
    val previousArtifactId: String? = null,
    @SerialName("max_chunks")
    val maxChunks: Int? = null,
)
