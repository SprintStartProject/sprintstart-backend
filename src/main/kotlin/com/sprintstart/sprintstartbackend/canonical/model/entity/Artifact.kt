package com.sprintstart.sprintstartbackend.canonical.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.Instant
import java.util.UUID

@Entity
class Artifact(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val sourceSystem : SourceSystem,
    @Column(nullable = false)
    val sourceId : String,
    @Column(nullable = false, length = 2048)
    val sourceUrl: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val artifactType: ArtifactType,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false)
    val bodyText: String,
    val mime : String,
    val language : String,
    val createdAtSource : Instant?,
    val updatedAtSource : Instant?,
    @Column(nullable = false)
    val ingestedAt: Instant = Instant.now(),
    @ManyToOne
    @JoinColumn(name = "ingestion_run_id")
    val ingestionRun : IngestionRun,
    @Column(name = "content_hash", nullable = false, length = 64)
    val hash: String,
    @Column(nullable = false)
    val version : String,
)
