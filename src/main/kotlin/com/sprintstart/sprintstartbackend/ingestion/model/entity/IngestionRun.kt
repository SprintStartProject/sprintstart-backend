package com.sprintstart.sprintstartbackend.ingestion.model.entity

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.Instant
import java.util.UUID

@Entity
class IngestionRun(
    @Id
    val id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val sourceSystem: SourceSystem,
    // Connector-neutral reference to the concrete source instance this run belongs to. Kept
    // generic (not GitHub-specific) so the entity stays abstract across connectors (GitHub, Jira,
    // upload, ...). Nullable because some sources (for example UPLOAD) have no instance, and older
    // runs predate these columns.
    // For GitHub, sourceInstanceId is the repository connection id.
    var sourceInstanceId: UUID? = null,
    // Denormalized, human-readable label of the source instance (for GitHub: "owner/name"). Stored
    // on the run so its history stays readable even if the instance is later decoupled or deleted,
    // and to avoid an extra lookup when listing runs.
    var sourceInstanceRef: String? = null,
    val startedAt: Instant = Instant.now(),
    var finishedAt: Instant? = null,
    @Column(nullable = false)
    var ingestedCount: Int = 0,
    @Column(nullable = false)
    var updatedCount: Int = 0,
    @Column(nullable = false)
    var deletedCount: Int = 0,
    @Column(nullable = false)
    var failedCount: Int = 0,
    @ElementCollection
    @Column(nullable = false)
    val failedItems: MutableList<FailedArtifact> = mutableListOf(),
    @ElementCollection
    @Column(nullable = false)
    val artifactIdsToDeindex: MutableList<String> = mutableListOf(),
    @ElementCollection
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val finishedTypes: MutableSet<FinishedTypes> = mutableSetOf(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IngestionRunStatus,
    var failureReason: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var aiSyncStatus: AiSyncStatus = AiSyncStatus.PENDING,
    var aiSyncFailureReason: String? = null,
)
