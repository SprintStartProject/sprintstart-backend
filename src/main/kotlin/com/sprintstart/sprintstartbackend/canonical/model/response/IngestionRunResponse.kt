package com.sprintstart.sprintstartbackend.canonical.model.response

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant
import java.util.UUID

data class IngestionRunResponse(
    val runId : UUID,
    val sourceSystem : SourceSystem,
    val startedAt: Instant,
    var finishedAt: Instant? = null,
    var ingestedCount : Int = 0,
    var updatedCount : Int = 0,
    var failedCount : Int = 0,
    var status: IngestionRunStatus = IngestionRunStatus.RUNNING,
)
