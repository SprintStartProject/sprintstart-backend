package com.sprintstart.sprintstartbackend.ingestion.external.model

import java.time.Instant
import java.util.UUID

data class NotionPageArtifactCommand(
    val sourceId: String,
    val sourceUrl: String,
    val sourceVersion: String,
    val title: String,
    val bodyText: String,
    val lastEditedTime: Instant,
    val metadata: NotionPageMetadataCommand,
)

data class NotionPageMetadataCommand(
    val connectionId: UUID,
    val pageId: String,
    val sections: List<NotionSectionCommand>,
    val tables: List<String>,
    val codeBlocks: List<NotionCodeBlockCommand>,
)

data class NotionSectionCommand(
    val heading: String,
    val level: Int,
)

data class NotionCodeBlockCommand(
    val language: String?,
    val code: String,
)

data class NotionArtifactWriteResult(
    val outcome: NotionArtifactWriteOutcome,
    val contentHash: String,
)

enum class NotionArtifactWriteOutcome {
    CREATED,
    UPDATED,
    UNCHANGED,
}
