package com.sprintstart.sprintstartbackend.ingestion.model.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class RunArtifactsIngestRequest(
    val artifacts: List<ArtifactAiIngestRequest>,
)
