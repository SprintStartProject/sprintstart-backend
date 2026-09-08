package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(
    description =
        "Ingestion health for a single connected source instance (for GitHub one connected " +
            "repository, for Jira one connected instance). Combines the connection's current status " +
            "with the counters of its latest run and the total number of stored artifacts for the " +
            "instance.",
)
data class SourceInstanceIngestionStatusResponse(
    @field:Schema(description = "Source system the connected instance belongs to, for example GITHUB or JIRA.")
    val sourceSystem: SourceSystem,
    @field:Schema(
        description =
            "Stable, connector-neutral identifier of the source instance. For GitHub \"owner/name\", " +
                "for Jira the instance URL.",
    )
    val sourceId: String,
    @field:Schema(
        description =
            "Human-readable label of the source instance. For GitHub \"owner/name\", for Jira the " +
                "instance display name.",
    )
    val displayName: String,
    @field:Schema(description = "Id of the connected repository (GitHub only); null for other source systems.")
    val repositoryId: UUID? = null,
    @field:Schema(description = "Owner of the connected repository (GitHub only); null for other source systems.")
    val owner: String? = null,
    @field:Schema(description = "Name of the connected repository (GitHub only); null for other source systems.")
    val name: String? = null,
    @field:Schema(description = "Web URL of the source instance (GitHub repository URL, or Jira instance URL).")
    val sourceUrl: String,
    @field:Schema(
        description =
            "Current connection status of the source instance: CONNECTED, UPDATING, OUT_OF_DATE, " +
                "FAILED or DISABLED. Distinct from a run's status, which uses its own vocabulary.",
    )
    val connectionStatus: String,
    @field:Schema(description = "Whether ingestion is currently enabled for this source instance.")
    val enabled: Boolean,
    @field:Schema(description = "Start time of the latest run for this instance. Null if it has never run.")
    val lastRunTime: Instant? = null,
    @field:Schema(description = "Number of newly ingested artifacts in the latest run.")
    val ingestedCount: Int = 0,
    @field:Schema(description = "Number of updated artifacts in the latest run.")
    val updatedCount: Int = 0,
    @field:Schema(description = "Number of deleted artifacts in the latest run.")
    val deletedCount: Int = 0,
    @field:Schema(description = "Number of failed artifacts in the latest run.")
    val failedCount: Int = 0,
    @field:Schema(description = "Failed items captured for the latest run of this source instance.")
    val failedItems: List<FailedArtifact> = emptyList(),
    @field:Schema(description = "Total number of ingested artifacts currently stored for this source instance.")
    val artifactCount: Long = 0,
    @field:Schema(description = "Timestamp of the last commits snapshot. Null if no snapshot exists yet.")
    val lastCommitsSyncAt: Instant? = null,
    @field:Schema(description = "Timestamp of the last issues snapshot. Null if no snapshot exists yet.")
    val lastIssuesSyncAt: Instant? = null,
    @field:Schema(description = "Timestamp of the last pull requests snapshot. Null if no snapshot exists yet.")
    val lastPullRequestsSyncAt: Instant? = null,
)
