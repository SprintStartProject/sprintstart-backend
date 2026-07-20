package com.sprintstart.sprintstartbackend.upload.external.model

import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import java.time.Instant
import java.util.UUID

data class UploadedArtifactDto(
    val id: UUID,
    val filename: String,
    val hash: String,
    val uploadedAt: Instant,
    val mime: String,
    val storagePath: String,
    val uploaderId: UUID,
)

fun UploadedArtifact.toDto() = UploadedArtifactDto(
    id = this.id,
    filename = this.filename,
    hash = this.hash,
    uploadedAt = this.uploadedAt,
    mime = this.mime,
    storagePath = this.storagePath,
    uploaderId = this.uploaderId,
)
