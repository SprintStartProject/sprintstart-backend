package com.sprintstart.sprintstartbackend.ingestion.model.entity

import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
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
    /**
     * The source instance this run belongs to (for GitHub, the repository connection id); null for sources
     * without one, such as uploads.
     */
    var sourceInstanceId: UUID? = null,
    /**
     * Readable label of the source instance (for GitHub, "owner/name"), kept so the run stays readable after
     * the instance is gone.
     */
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
    /**
     * Artifacts this run changed that an earlier run stored. They keep pointing at that run, so without this
     * set they would never reach the AI index again. A set, because one entity graph cannot fetch two bags.
     */
    @ElementCollection
    @Column(nullable = false)
    val artifactIdsToReingest: MutableSet<UUID> = mutableSetOf(),
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
