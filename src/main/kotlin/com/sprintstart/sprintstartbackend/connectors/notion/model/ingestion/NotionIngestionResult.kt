package com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion

import java.util.UUID

internal data class NotionIngestionResult(
    val runId: UUID,
    val connectionId: UUID,
    val outcome: NotionIngestionOutcome,
    val failure: NotionIngestionFailure? = null,
)

internal data class NotionIngestionFailure(
    val stage: NotionIngestionFailureStage,
    val message: String,
)

internal enum class NotionIngestionOutcome {
    CREATED,
    UPDATED,
    UNCHANGED,
    FAILED,
}

internal enum class NotionIngestionFailureStage {
    FETCHING,
    PARSING,
    PERSISTENCE,
}
