package com.sprintstart.sprintstartbackend.upload.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "uploaded_artifact",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_uploaded_artifact_hash",
            columnNames = ["hash"],
        ),
    ],
)
class UploadedArtifact(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var filename: String,
    @Column(nullable = false, length = 64)
    var hash: String,
    @Column(nullable = false)
    var uploadedAt: Instant = Instant.now(),
    @Column(nullable = false)
    var mime: String,
    @Column(nullable = false)
    var storagePath: String,
    @Column(name = "uploader_id", nullable = false)
    var uploaderId: UUID,
)
