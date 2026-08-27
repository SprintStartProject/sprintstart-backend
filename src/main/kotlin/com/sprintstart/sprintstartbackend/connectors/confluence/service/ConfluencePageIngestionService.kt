package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceIngestionException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionStatus
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ConfluenceStorageFormatParser
import com.sprintstart.sprintstartbackend.ingestion.external.ConfluenceArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactFailure
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/** Fetches, filters, parses, and submits one complete Confluence page ingestion batch. */
@Service
internal class ConfluencePageIngestionService(
    private val connectionService: ConfluenceConnectionRuntimeService,
    private val confluenceClient: ConfluenceClient,
    private val parser: ConfluenceStorageFormatParser,
    private val pageMapper: ConfluencePageArtifactMapper,
    private val ingestionApi: ConfluenceArtifactIngestionApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ingest(projectId: UUID, connectionId: UUID): ConfluenceIngestionResult {
        val connection = connectionService.getConnectionForIngestion(projectId, connectionId)
        requireEnabled(connection)
        val runId = UUID.randomUUID()
        ingestionApi.startRun(runId, connection.id, "${connection.baseUrl}|${connection.spaceId}")

        try {
            val fetched = confluenceClient.getPages(
                connection.baseUrl,
                connection.credentials,
                connection.spaceId,
            )
            val pages = fetched.successfulPages.distinctBy { page -> page.id }
            val relationshipIndex = buildRelationshipIndex(pages)
            val eligiblePages = pages.filter { page -> connection.allowsPage(page.id) }
            val failures = fetched.failures
                .map { failure ->
                    ConfluenceIngestionFailure(
                        pageId = failure.pageId,
                        stage = ConfluenceIngestionFailureStage.RESTRICTIONS,
                        message = RESTRICTIONS_FAILURE_MESSAGE,
                    )
                }.toMutableList()
            val artifactCommands = mapEligiblePages(
                connection = connection,
                pages = eligiblePages,
                relationshipIndex = relationshipIndex,
                failures = failures,
            )
            val failureCommands = failures.map { failure ->
                ConfluencePageArtifactFailure(
                    pageId = failure.pageId,
                    sourceUrl = null,
                    reason = failure.message,
                )
            }
            val persisted = ingestionApi.persistBatch(
                ConfluenceArtifactBatchCommand(
                    runId = runId,
                    projectId = projectId,
                    artifacts = artifactCommands,
                    failures = failureCommands,
                ),
            )
            ingestionApi.finishRun(runId)
            return ConfluenceIngestionResult(
                runId = runId,
                connectionId = connectionId,
                discovered = fetched.successfulPages.size + fetched.failures.size,
                eligible = eligiblePages.size,
                filtered = pages.size - eligiblePages.size,
                created = persisted.created,
                updated = persisted.updated,
                unchanged = persisted.unchanged,
                failed = persisted.failed,
                failures = failures,
                status = resultStatus(persisted.created, persisted.updated, persisted.failed),
            )
        } catch (exception: RuntimeException) {
            markRunFailed(runId)
            throw sanitizedTerminalException(exception)
        }
    }

    private fun mapEligiblePages(
        connection: ConfluenceConnectionIngestionSnapshot,
        pages: List<ConfluencePage>,
        relationshipIndex: RelationshipIndex,
        failures: MutableList<ConfluenceIngestionFailure>,
    ): List<ConfluencePageArtifactCommand> {
        return pages.mapNotNull { page ->
            if (page.id in relationshipIndex.selfParentPageIds) {
                failures += ConfluenceIngestionFailure(
                    pageId = page.id,
                    stage = ConfluenceIngestionFailureStage.HIERARCHY,
                    message = SELF_PARENT_FAILURE_MESSAGE,
                )
                return@mapNotNull null
            }
            try {
                val parsedBody = parser.parse(page.storage.value)
                pageMapper.toCommand(
                    connection = connection,
                    page = page,
                    parsedBody = parsedBody,
                    relationships = relationshipIndex.byPageId[page.id].orEmpty(),
                )
            } catch (@Suppress("SwallowedException") exception: RuntimeException) {
                failures += ConfluenceIngestionFailure(
                    pageId = page.id,
                    stage = ConfluenceIngestionFailureStage.PARSING,
                    message = PARSING_FAILURE_MESSAGE,
                )
                null
            }
        }
    }

    private fun buildRelationshipIndex(pages: List<ConfluencePage>): RelationshipIndex {
        val selfParentPageIds = pages
            .filter { page -> page.parentType.equals("page", ignoreCase = true) && page.parentId == page.id }
            .map { page -> page.id }
            .toSet()
        val childIdsByParentId = pages
            .filter { page ->
                page.parentType.equals("page", ignoreCase = true) &&
                    page.parentId != null &&
                    page.parentId != page.id
            }.groupBy { page -> requireNotNull(page.parentId) }
            .mapValues { (_, children) -> children.map { child -> child.id }.distinct().sorted() }
        val byPageId = pages.associate { page ->
            val relationships = buildList {
                if (
                    page.parentType.equals("page", ignoreCase = true) &&
                    page.parentId != null &&
                    page.parentId != page.id
                ) {
                    add(ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, page.parentId))
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

    private fun resultStatus(created: Int, updated: Int, failed: Int): ConfluenceIngestionStatus {
        if (failed == 0) {
            return ConfluenceIngestionStatus.COMPLETED
        }
        return if (created > 0 || updated > 0) {
            ConfluenceIngestionStatus.PARTIAL
        } else {
            ConfluenceIngestionStatus.FAILED
        }
    }

    private fun requireEnabled(connection: ConfluenceConnectionIngestionSnapshot) {
        if (!connection.sourceEnabled) {
            throw ConfluenceIngestionException()
        }
    }

    private fun sanitizedTerminalException(exception: RuntimeException): RuntimeException {
        if (exception is ConfluenceClientException || exception is ConfluenceInvalidResponseException) {
            return exception
        }
        return ConfluenceIngestionException()
    }

    private fun markRunFailed(runId: UUID) {
        try {
            ingestionApi.failRun(runId, TERMINAL_FAILURE_MESSAGE)
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            logger.error("Unable to mark Confluence ingestion run {} as failed", runId)
        }
    }

    private data class RelationshipIndex(
        val byPageId: Map<String, List<ConfluenceRelationshipCommand>>,
        val selfParentPageIds: Set<String>,
    )

    private companion object {
        const val RESTRICTIONS_FAILURE_MESSAGE = "Confluence page restrictions were unavailable"
        const val SELF_PARENT_FAILURE_MESSAGE = "Confluence page cannot be its own parent"
        const val PARSING_FAILURE_MESSAGE = "Confluence page storage format could not be parsed"
        const val TERMINAL_FAILURE_MESSAGE = "Confluence page ingestion terminated before persistence"
    }
}
