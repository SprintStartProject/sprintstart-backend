package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtifactAiIngestResponse(
    @SerialName("artifact_id")
    val artifactId: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
)
