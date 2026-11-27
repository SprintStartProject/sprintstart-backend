package com.sprintstart.sprintstartbackend.connectors.notion.external.ingestion

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
    val workspaceName: String = "Notion workspace",
    val edits: List<NotionPageEditCommand> = emptyList(),
)

/**
 * Somebody who wrote a page: Notion names only who created it and who edited it last.
 *
 * @property userId The Notion user ID.
 * @property editedAt When they created or last edited it.
 */
data class NotionPageEditCommand(
    val userId: String,
    val editedAt: Instant,
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
