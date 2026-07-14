package com.sprintstart.sprintstartbackend.upload.external.events.ingestion

import java.util.UUID

data class UploadBatchDeletionFinishedEvent(
    val transactionId: UUID,
    val removerId: UUID,
    val deleteArtifactOutcomes: Set<UploadArtifactOperationOutcome>,
)
