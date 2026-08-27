package com.sprintstart.sprintstartbackend.ingestion.external.model

import java.time.Instant
import java.util.UUID

data class ConfluenceArtifactBatchCommand(
    val runId: UUID,
    val projectId: UUID,
    val artifacts: List<ConfluencePageArtifactCommand>,
    val failures: List<ConfluencePageArtifactFailure> = emptyList(),
)

data class ConfluencePageArtifactCommand(
    val sourceId: String,
    val sourceUrl: String?,
    val sourceVersion: String,
    val title: String,
    val bodyText: String,
    val versionCreatedAt: Instant,
    val metadata: ConfluencePageMetadataCommand,
)

data class ConfluencePageMetadataCommand(
    val connectionId: UUID,
    val tenantBaseUrl: String,
    val spaceId: String,
    val spaceKey: String,
    val pageId: String,
    val versionNumber: Int,
    val versionCreatedAt: Instant,
    val parentId: String?,
    val parentType: String?,
    val webUiPath: String?,
    val sections: List<ConfluenceSectionCommand>,
    val tables: List<String>,
    val codeBlocks: List<ConfluenceCodeBlockCommand>,
    val relationships: List<ConfluenceRelationshipCommand>,
    val sourceAcl: ConfluenceSourceAclCommand,
)

data class ConfluenceSectionCommand(
    val heading: String,
    val level: Int,
)

data class ConfluenceCodeBlockCommand(
    val language: String?,
    val code: String,
)

data class ConfluenceRelationshipCommand(
    val type: ConfluenceRelationshipType,
    val targetSourceArtifactId: String,
)

enum class ConfluenceRelationshipType {
    PARENT_OF,
    CHILD_OF,
}

data class ConfluenceSourceAclCommand(
    val userAccountIds: List<String>,
    val groupIds: List<String>,
)

data class ConfluencePageArtifactFailure(
    val pageId: String,
    val sourceUrl: String?,
    val reason: String,
)

data class ConfluenceArtifactBatchResult(
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val failed: Int,
)
