package com.sprintstart.sprintstartbackend.upload.events

import java.util.UUID

data class ArtifactUploadedEvent(
    val artifactId: UUID,
    val filename: String,
    val storagePath: String,
    val mime: String,
)
