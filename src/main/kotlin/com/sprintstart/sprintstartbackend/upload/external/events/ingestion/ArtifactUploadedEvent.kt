package com.sprintstart.sprintstartbackend.upload.external.events.ingestion

import java.time.Instant
import java.util.UUID

data class ArtifactUploadedEvent(
    val transactionId: UUID,
    val projectId: UUID,
    val artifactId: UUID,
    val filename: String,
    val storagePath: String,
    val mime: String,
    val hash: String,
    val uploadedAt: Instant,
    val uploaderId: UUID,
)
