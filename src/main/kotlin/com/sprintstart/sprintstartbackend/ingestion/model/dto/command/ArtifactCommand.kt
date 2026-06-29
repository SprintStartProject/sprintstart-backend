package com.sprintstart.sprintstartbackend.ingestion.model.dto.command

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import java.time.Instant
import java.util.UUID

data class ArtifactCommand(
    val ingestionRunId: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    val title: String?,
    val bodyText: String?,
    val mime: String?,
    val language: String?,
    val createdAtSource: Instant?,
    val updatedAtSource: Instant?,
    val hash: String?,
)
