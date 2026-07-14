package com.sprintstart.sprintstartbackend.artifacts.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.util.UUID

/**
 * One source artifact the AI cited while generating an [ArtifactSummary].
 */
@Entity
class ArtifactSummaryCitation(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "artifact_summary_id")
    val artifactSummary: ArtifactSummary,
    @Column(name = "cited_artifact_id", nullable = false)
    val citedArtifactId: UUID,
    @Column(nullable = false)
    val filename: String,
    @Column(name = "source_url", length = 2048)
    val sourceUrl: String? = null,
)
