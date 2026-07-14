package com.sprintstart.sprintstartbackend.upload.external.events.ingestion

import java.util.UUID

/**
 * `id` is nullable because validation or storage failures can happen before an uploaded artifact
 * row exists.
 */
data class UploadArtifactOperationOutcome(
    val id: UUID?,
    val filename: String,
    val status: UploadArtifactStatus,
    val error: String? = null,
)
