package com.sprintstart.sprintstartbackend.upload.external.events

import java.io.Serializable
import java.util.UUID

data class AiIngestRequest(
    val artifactId: UUID,
    val filename: String,
    val content: String,
) : Serializable