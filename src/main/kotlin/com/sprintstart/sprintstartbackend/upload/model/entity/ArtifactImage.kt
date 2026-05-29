package com.sprintstart.sprintstartbackend.upload.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "artifact_image")
class ArtifactImage(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "artifact_id",
        nullable = false,
    )
    var artifact: UploadedArtifact,
    @Column(nullable = false)
    var originalPath: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "image_artifact_id",
        nullable = false,
    )
    var imageArtifact: UploadedArtifact,
)
