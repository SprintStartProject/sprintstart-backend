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
    val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val sourceSystem : SourceSystem,
    @Column(nullable = false)
    val startedAt: Instant,
    var finishedAt: Instant? = null,
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
