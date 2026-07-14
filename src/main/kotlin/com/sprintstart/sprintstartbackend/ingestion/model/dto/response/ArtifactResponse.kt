package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import java.time.Instant
import java.util.UUID

data class ArtifactResponse(
    val id: UUID = UUID.randomUUID(),
    var title: String?,
    val sourceSystem: SourceSystem,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    val ingestedAt: Instant,
    val metadata: String,
)
