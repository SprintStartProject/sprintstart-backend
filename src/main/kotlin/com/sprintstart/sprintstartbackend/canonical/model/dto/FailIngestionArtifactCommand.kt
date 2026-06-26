package com.sprintstart.sprintstartbackend.canonical.model.dto

import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import java.util.UUID

data class FailIngestionArtifactCommand(
    val runId: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String?,
    val artifactIdentifier: String,
    val step: IngestionStep,
    val reason: String,
)