package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(
    description =
        "API representation of a single ingestion run. " +
            "Used by operational views that need run timing, counters, and failure summaries.",
)
data class IngestionRunResponse(
    @field:Schema(description = "Stable identifier of the ingestion run.")
    val runId: UUID,
    @field:Schema(description = "Source system that produced this run, for example GITHUB.")
    val sourceSystem: SourceSystem,
    @field:Schema(description = "Timestamp when the run started processing.")
    val startedAt: Instant,
    @field:Schema(description = "Timestamp when the run finished. Null while a run is still in progress.")
    val finishedAt: Instant? = null,
    @field:Schema(description = "Number of new ingestion artifacts created during the run.")
    val ingestedCount: Int = 0,
    @field:Schema(description = "Number of existing ingestion artifacts updated during the run.")
    val updatedCount: Int = 0,
    @field:Schema(description = "Number of source artifacts that failed to ingest during the run.")
    val failedCount: Int = 0,
    @field:Schema(description = "Failure details captured for individual source artifacts in this run.")
    val failedItems: MutableList<FailedArtifact>,
    val status: IngestionRunStatus,
    @field:Schema(
        description =
            "Whether this run's artifacts have actually reached the AI service's index. " +
                "`status` above only reflects local fetch-and-store, so a run can be COMPLETED " +
                "while this is still PENDING or has moved to FAILED.",
    )
    val aiSyncStatus: AiSyncStatus,
    @field:Schema(description = "Failure detail when aiSyncStatus is FAILED.")
    val aiSyncFailureReason: String? = null,
)
