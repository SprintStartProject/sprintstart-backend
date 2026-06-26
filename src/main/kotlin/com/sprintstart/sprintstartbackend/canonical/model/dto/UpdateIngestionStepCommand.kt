package com.sprintstart.sprintstartbackend.canonical.model.dto

import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import java.util.UUID

data class UpdateIngestionStepCommand(
    val runId: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String? = null,
    val step: IngestionStep,
)