package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiIngestRequest(
    @SerialName("artifact_id")
    val artifactId: String,
    val filename: String,
    val content: String,
)
