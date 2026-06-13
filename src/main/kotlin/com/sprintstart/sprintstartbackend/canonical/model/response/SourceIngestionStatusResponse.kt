package com.sprintstart.sprintstartbackend.canonical.model.response

import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class SourceIngestionStatusResponse(
    val sourceSystem : SourceSystem,
    @Schema(description = "Timestamp of the latest ingestion run. Null if the source has never been ingested.")
    val lastRunTime: Instant?,
    val ingestedCount : Int = 0,
    val updatedCount : Int = 0,
    val failedCount : Int = 0,
    val failedItems : List<FailedArtifactResponse> = emptyList(),
)
