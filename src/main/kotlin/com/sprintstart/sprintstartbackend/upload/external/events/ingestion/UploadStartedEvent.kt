package com.sprintstart.sprintstartbackend.upload.external.events.ingestion

import java.util.UUID

/**
 * The transaction id is also the ingestion run id for every upload event in the batch.
 */
data class UploadStartedEvent(
    val transactionId: UUID,
)
