package com.sprintstart.sprintstartbackend.ingestion.model.entity

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
    val startedAt: Instant = Instant.now(),
    var finishedAt: Instant? = null,
    @Column(nullable = false)
    var ingestedCount: Int = 0,
    @Column(nullable = false)
    var updatedCount: Int = 0,
    @Column(nullable = false)
    var failedCount: Int = 0,
    @ElementCollection
    @Column(nullable = false)
    val failedItems: MutableList<FailedArtifact> = mutableListOf(),
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val finishedTypes: MutableSet<FinishedTypes> = mutableSetOf(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IngestionRunStatus,
    var failureReason: String? = null,
)
