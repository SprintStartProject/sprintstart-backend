package com.sprintstart.sprintstartbackend.ingestion.external.model

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import java.time.Instant
import java.util.UUID

data class ArtifactDto(
    val id: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String?,
    val artifactType: String,
    val title: String?,
    val content: String?,
    val mime: String?,
    val language: String?,
    val metadata: String,
    val createdAtSource: Instant?,
    val updatedAtSource: Instant?,
    val ingestedAt: Instant?,
    val ingestionRunId: UUID?,
    val hash: String?,
)

fun Artifact.toDto() = ArtifactDto(
    id = id,
    sourceSystem = sourceSystem,
    sourceId = sourceId,
    sourceUrl = sourceUrl,
    artifactType = artifactType.name,
    title = title,
    content = content,
    mime = mime,
    language = language,
    metadata = metadata,
    createdAtSource = createdAtSource,
    updatedAtSource = updatedAtSource,
    ingestedAt = ingestedAt,
    ingestionRunId = ingestionRun.id,
    hash = hash,
)
