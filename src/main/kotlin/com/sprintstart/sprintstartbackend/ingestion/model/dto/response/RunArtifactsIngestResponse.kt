package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class RunArtifactsIngestResponse(
    val artifacts: List<ArtifactAiIngestResponse>,
)
