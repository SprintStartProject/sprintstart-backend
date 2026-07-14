package com.sprintstart.sprintstartbackend.artifacts.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.Instant
import java.util.UUID

/**
 * Cached AI-generated summary of an artifact, keyed by the artifact's own id.
 *
 * Generating a summary is a real LLM call, so it is not repeated on every read: [sourceHash] is
 * the content hash the artifact had when this summary was generated (see [Artifact][com.sprintstart.
 * sprintstartbackend.ingestion.model.entity.Artifact] / [UploadedArtifact][com.sprintstart.
 * sprintstartbackend.upload.model.entity.UploadedArtifact]). A read is served from this cache as
 * long as the artifact's current hash still matches; once the artifact's content changes, the
 * hash no longer matches and a fresh summary is generated and stored here, replacing this row.
 */
@Entity
class ArtifactSummary(
    @Id
    val artifactId: UUID,
    @Column(columnDefinition = "TEXT", nullable = false)
    var summary: String,
    @Column(name = "source_hash", length = 64)
    var sourceHash: String?,
    @Column(nullable = false)
    var generatedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "artifactSummary", cascade = [CascadeType.ALL], orphanRemoval = true)
    var citations: MutableList<ArtifactSummaryCitation> = mutableListOf(),
)
