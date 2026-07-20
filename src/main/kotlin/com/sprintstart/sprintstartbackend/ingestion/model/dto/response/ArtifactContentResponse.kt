package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

sealed interface ArtifactContentResult

data class ArtifactContentResponse(
    val content: ByteArray,
    val mime: String,
) : ArtifactContentResult

data class ArtifactContentRedirectResponse(
    val url: String,
) : ArtifactContentResult
