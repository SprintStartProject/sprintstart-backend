package com.sprintstart.sprintstartbackend.ingestion.model.entity

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
    @Column(nullable = false)
    val sourceSystem: SourceSystem,
    @Column(nullable = false)
    val sourceId: String,
    @Column(length = 2048)
    val sourceUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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
    val createdAtSource: Instant?,
    val updatedAtSource: Instant?,
    @Column(nullable = false)
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
