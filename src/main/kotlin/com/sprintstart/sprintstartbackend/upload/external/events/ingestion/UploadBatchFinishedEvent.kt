package com.sprintstart.sprintstartbackend.upload.external.events.ingestion

import java.util.UUID

data class UploadBatchFinishedEvent(
    val transactionId: UUID,
    val uploaderId: UUID,
    val artifactsId: Set<UUID>,
    val linkedImages: Set<UUID>,
    val uploadArtifactOperationOutcomes: Set<UploadArtifactOperationOutcome>,
)
