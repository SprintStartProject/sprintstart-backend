package com.sprintstart.sprintstartbackend.ingestion.model.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class RunArtifactsAiSyncRequest(
    val artifactsToIngest: List<ArtifactAiIngestRequest>,
    val artifactsToDeindex: List<String>,
)
