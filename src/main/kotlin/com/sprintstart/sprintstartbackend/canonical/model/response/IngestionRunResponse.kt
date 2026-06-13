package com.sprintstart.sprintstartbackend.canonical.model.response

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import java.time.Instant
import java.util.UUID

data class IngestionRunResponse(
    val runId : UUID,
    val sourceSystem : SourceSystem,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val ingestedCount : Int = 0,
    val updatedCount : Int = 0,
    val failedCount : Int = 0,
    val status: IngestionRunStatus = IngestionRunStatus.RUNNING,
)
