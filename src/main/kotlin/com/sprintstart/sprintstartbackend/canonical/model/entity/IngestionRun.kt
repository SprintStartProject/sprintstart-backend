package com.sprintstart.sprintstartbackend.canonical.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.Instant
import java.util.*

@Entity
class IngestionRun(
    @Id
    val id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val sourceSystem : SourceSystem,
    val startedAt: Instant = Instant.now(),
    var finishedAt: Instant? = null,
    @Column(nullable = false)
    val expectedCount : Int,
    @Column(nullable = false)
    val processedCount : Int = 0,
    @Column(nullable = false)
    var ingestedCount : Int = 0,
    @Column(nullable = false)
    var updatedCount : Int = 0,
    @Column(nullable = false)
    var failedCount : Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IngestionRunStatus = IngestionRunStatus.RUNNING,
)
