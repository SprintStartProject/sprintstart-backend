package com.sprintstart.sprintstartbackend.ingestion.model.dto.command

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import java.util.UUID

data class ArtifactFailedCommand(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val sourceId: String?,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    val reason: String,
)
