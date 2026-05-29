package com.sprintstart.sprintstartbackend.upload.model.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "artifact_image")
class ArtifactImage(

    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "artifact_id",
        nullable = false
    )
    var artifact: UploadedArtifact,

    @Column(nullable = false)
    var originalPath: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "image_artifact_id",
        nullable = false
    )
    var imageArtifact: UploadedArtifact,
)