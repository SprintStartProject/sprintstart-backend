package com.sprintstart.sprintstartbackend.ingestion.model.entity

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
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
    @Column(name = "source_system", nullable = false)
    val sourceSystem: SourceSystem,
    @Column(name = "source_id", nullable = false)
    val sourceId: String,
    @Column(name = "source_url", length = 2048)
    val sourceUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false)
    val artifactType: ArtifactType,
    var title: String?,
    @Column(columnDefinition = "TEXT")
    var content: String?,
    val mime: String?,
    val language: String?,
    @Column(nullable = false)
    val metadata: String = "{}",
    @ElementCollection
    @CollectionTable(
        name = "artifact_projects",
        joinColumns = [JoinColumn(name = "artifact_id")],
    )
    @Column(name = "project_id", nullable = false)
    // Add companion obj to Artifact to have Artifact.create
    // to keep internal state hidden
    private val projectIdsInternal: MutableSet<UUID> = mutableSetOf(),
    @Column(name = "created_at_source")
    val createdAtSource: Instant?,
    @Column(name = "updated_at_source")
    val updatedAtSource: Instant?,
    @Column(name = "ingested_at", nullable = false)
    val ingestedAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingestion_run_id")
    val ingestionRun: IngestionRun,
    @Column(name = "content_hash", length = 64)
    var hash: String?,
) {
    val projectIds: Set<UUID>
        get() = projectIdsInternal.toSet()

    fun addProjectIds(projectIds: Set<UUID>) {
        projectIdsInternal.addAll(projectIds)
    }

    fun addProjectId(projectId: UUID) {
        projectIdsInternal.add(projectId)
    }
}
