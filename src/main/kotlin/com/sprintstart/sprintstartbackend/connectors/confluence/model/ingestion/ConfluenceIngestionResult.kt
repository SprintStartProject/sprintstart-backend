package com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion

import java.util.UUID

internal data class ConfluenceIngestionResult(
    val runId: UUID,
    val connectionId: UUID,
    val discovered: Int,
    val eligible: Int,
    val filtered: Int,
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val failed: Int,
    val failures: List<ConfluenceIngestionFailure>,
    val status: ConfluenceIngestionStatus,
)

internal data class ConfluenceIngestionFailure(
    val pageId: String,
    val stage: ConfluenceIngestionFailureStage,
    val httpStatus: Int? = null,
    val attempts: Int? = null,
    val message: String,
)

internal enum class ConfluenceIngestionFailureStage {
    RESTRICTIONS,
    HIERARCHY,
    PARSING,
    MAPPING,
    PERSISTENCE,
}

internal enum class ConfluenceIngestionStatus {
    COMPLETED,
    PARTIAL,
    FAILED,
}
