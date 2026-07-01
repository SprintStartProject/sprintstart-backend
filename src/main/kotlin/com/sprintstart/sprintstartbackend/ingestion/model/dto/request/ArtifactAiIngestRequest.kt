package com.sprintstart.sprintstartbackend.ingestion.model.dto.request

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import kotlinx.serialization.Serializable

@Serializable
data class ArtifactAiIngestRequest(
    val artifactId: String,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    var title: String?,
    var bodyText: String?,
    val mime: String?,
    val language: String?,
)
