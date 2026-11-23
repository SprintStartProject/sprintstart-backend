package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceRelationshipType
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionStatus

internal const val RESTRICTIONS_FAILURE_MESSAGE = "Confluence page restrictions were unavailable"
internal const val SELF_PARENT_FAILURE_MESSAGE = "Confluence page cannot be its own parent"
internal const val PARSING_FAILURE_MESSAGE = "Confluence page storage format could not be parsed"
internal const val MAPPING_FAILURE_MESSAGE = "Confluence page could not be mapped to an artifact"
internal const val PERSISTENCE_FAILURE_MESSAGE = "Confluence page artifact could not be persisted"

internal data class RelationshipIndex(
    val byPageId: Map<String, List<ConfluenceRelationshipCommand>>,
    val selfParentPageIds: Set<String>,
)

internal fun buildRelationshipIndex(pages: List<ConfluencePage>): RelationshipIndex {
    val selfParentPageIds = pages
        .filter { page -> page.parentType.equals("page", ignoreCase = true) && page.parentId == page.id }
        .map { page -> page.id }
        .toSet()
    val childIdsByParentId = pages
        .filter { page -> page.otherPageParentId() != null }
        .groupBy { page -> requireNotNull(page.otherPageParentId()) }
        .mapValues { (_, children) -> children.map { child -> child.id }.distinct().sorted() }
    val byPageId = pages.associate { page ->
        val relationships = buildList {
            page.otherPageParentId()?.let {
                add(ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, it))
            }
            childIdsByParentId[page.id].orEmpty().forEach { childId ->
                add(ConfluenceRelationshipCommand(ConfluenceRelationshipType.PARENT_OF, childId))
            }
        }.distinct().sortedWith(
            compareBy(
                { relationship -> relationship.type.name },
                { relationship -> relationship.targetSourceArtifactId },
            ),
        )
        page.id to relationships
    }
    return RelationshipIndex(byPageId, selfParentPageIds)
}

/** The id of the page this page hangs under, or null when its parent is not a different page. */
internal fun ConfluencePage.otherPageParentId(): String? =
    parentId?.takeIf { parentType.equals("page", ignoreCase = true) && it != id }

internal fun resultStatus(successfulItemCount: Int, failed: Int): ConfluenceIngestionStatus {
    if (failed == 0) {
        return ConfluenceIngestionStatus.COMPLETED
    }
    return if (successfulItemCount > 0) {
        ConfluenceIngestionStatus.PARTIAL
    } else {
        ConfluenceIngestionStatus.FAILED
    }
}

internal fun failureMessage(stage: ConfluenceIngestionFailureStage): String =
    when (stage) {
        ConfluenceIngestionFailureStage.RESTRICTIONS -> RESTRICTIONS_FAILURE_MESSAGE
        ConfluenceIngestionFailureStage.HIERARCHY -> SELF_PARENT_FAILURE_MESSAGE
        ConfluenceIngestionFailureStage.PARSING -> PARSING_FAILURE_MESSAGE
        ConfluenceIngestionFailureStage.MAPPING -> MAPPING_FAILURE_MESSAGE
        ConfluenceIngestionFailureStage.PERSISTENCE -> PERSISTENCE_FAILURE_MESSAGE
    }

internal fun ConfluencePageFailure.toIngestionFailure() =
    ConfluenceIngestionFailure(
        pageId = pageId,
        stage = ConfluenceIngestionFailureStage.RESTRICTIONS,
        httpStatus = httpStatus,
        attempts = attempts,
        message = RESTRICTIONS_FAILURE_MESSAGE,
    )

internal fun ConfluenceIngestionFailure.toArtifactFailure() =
    ConfluencePageArtifactFailure(
        pageId = pageId,
        sourceUrl = null,
        stage = stage.toArtifactFailureStage(),
        httpStatus = httpStatus,
        attempts = attempts,
        reason = message,
    )

internal fun ConfluencePageArtifactFailure.toIngestionFailure() =
    ConfluenceIngestionFailure(
        pageId = pageId,
        stage = ConfluenceIngestionFailureStage.PERSISTENCE,
        httpStatus = httpStatus,
        attempts = attempts,
        message = PERSISTENCE_FAILURE_MESSAGE,
    )

internal fun ConfluenceIngestionFailureStage.toArtifactFailureStage(): ConfluenceArtifactFailureStage {
    return when (this) {
        ConfluenceIngestionFailureStage.RESTRICTIONS -> ConfluenceArtifactFailureStage.RESTRICTIONS
        ConfluenceIngestionFailureStage.HIERARCHY -> ConfluenceArtifactFailureStage.HIERARCHY
        ConfluenceIngestionFailureStage.PARSING -> ConfluenceArtifactFailureStage.PARSING
        ConfluenceIngestionFailureStage.MAPPING -> ConfluenceArtifactFailureStage.MAPPING
        ConfluenceIngestionFailureStage.PERSISTENCE -> ConfluenceArtifactFailureStage.PERSISTENCE
    }
}
