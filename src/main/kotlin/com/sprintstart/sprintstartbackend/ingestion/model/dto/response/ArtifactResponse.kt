package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import java.time.Instant
import java.util.UUID

data class ArtifactResponse(
    val id: UUID = UUID.randomUUID(),
    var title: String?,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    val ingestedAt: Instant,
    /**
     * When ingestion last saw the artifact's content change, or null while it still matches what
     * was first imported. Distinct from [ingestedAt], which stays at the first import.
     */
    val lastChangedAt: Instant?,
    val metadata: String,
)
