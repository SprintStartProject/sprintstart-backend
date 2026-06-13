package com.sprintstart.sprintstartbackend.canonical.model.response

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

data class SourceIngestionStatusResponse(
    val sourceSystem : SourceSystem,
    val hasRuns : Boolean,
    val lastRunTime: Instant?,
    val ingestedCount : Int = 0,
    val updatedCount : Int = 0,
    val failedCount : Int = 0,
    val failedItems : List<FailedArtifactResponse>,
)
