package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(
    description =
        "Latest ingestion status for a source system. " +
            "This is a compact summary intended for dashboards and operational status views.",
)
data class SourceIngestionStatusResponse(
    @field:Schema(description = "Source system the status row belongs to.")
    val sourceSystem: SourceSystem,
    @field:Schema(description = "Timestamp of the latest ingestion run. Null if the source has never run.")
    val lastRunTime: Instant?,
    @field:Schema(description = "Number of newly ingested artifacts in the latest run.")
    val ingestedCount: Int = 0,
    @field:Schema(description = "Number of updated artifacts in the latest run.")
    val updatedCount: Int = 0,
    @field:Schema(description = "Number of failed artifacts in the latest run.")
    val failedCount: Int = 0,
    @field:Schema(description = "Failed items captured for the latest run of this source.")
    val failedItems: List<FailedArtifact> = emptyList(),
    val status: IngestionRunStatus? = null,
)
