package com.sprintstart.sprintstartbackend.upload.external.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiIngestResponse(
    @SerialName("artifact_id")
    val artifactId: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
)
