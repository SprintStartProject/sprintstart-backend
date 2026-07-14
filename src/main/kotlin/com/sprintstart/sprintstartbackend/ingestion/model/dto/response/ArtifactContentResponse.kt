package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

data class ArtifactContentResponse(
    val content: ByteArray,
    val mime: String,
)
