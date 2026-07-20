package com.sprintstart.sprintstartbackend.upload.external.events.ingestion

import java.util.UUID

data class UploadFileDeletedEvent(
    val transactionId: UUID,
    val uploadArtifactId: UUID,
)
