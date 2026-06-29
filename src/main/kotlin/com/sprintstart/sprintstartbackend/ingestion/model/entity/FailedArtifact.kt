package com.sprintstart.sprintstartbackend.ingestion.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class FailedArtifact(
    val sourceId: String?,
    val artifactType: ArtifactType,
    @Column(length = 2048)
    val sourceUrl: String?,
    @Column(columnDefinition = "TEXT")
    val reason: String,
)
