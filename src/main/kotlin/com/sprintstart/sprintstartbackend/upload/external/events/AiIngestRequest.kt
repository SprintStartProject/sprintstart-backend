package com.sprintstart.sprintstartbackend.upload.external.events

import kotlinx.serialization.Serializable

@Serializable
data class AiIngestRequest(
    val artifactId: String,
    val filename: String,
    val content: String,
)
