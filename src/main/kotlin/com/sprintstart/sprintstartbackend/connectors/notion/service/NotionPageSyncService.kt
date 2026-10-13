package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionBlockParser
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionSyncedPage
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Synchronizes one Notion page within a connection-wide ingestion run.
 *
 * Scheduled runs skip a page whose edit timestamp and stored hash are unchanged. Manual runs may
 * force content retrieval so edits hidden by Notion's timestamp precision are still detected.
 */
@Service
internal class NotionPageSyncService(
    private val notionClient: NotionClient,
    private val parser: NotionBlockParser,
    private val artifactMapper: NotionPageArtifactMapper,
    private val syncedPageService: NotionSyncedPageService,
) {
    /** Fetches, parses, and persists one page while translating stage failures into a page-local error. */
    suspend fun sync(
        runId: UUID,
        connection: NotionWorkspaceConnection,
        token: String,
        page: NotionApiPageResponse,
        state: NotionSyncedPage?,
        forceRefresh: Boolean,
    ) {
        val editedAt = step("Notion returned invalid page metadata") { Instant.parse(page.lastEditedTime) }
        val unchanged = state?.contentHash != null && state.lastEditedTime == editedAt && state.unlinkedAt == null
        if (!forceRefresh && unchanged) return

        val tree = step("Notion page content could not be fetched") { notionClient.getBlockTree(token, page.id) }
        val command = step("Notion page blocks could not be parsed") {
            artifactMapper.toCommand(connection, page, parser.parse(tree))
        }
        step("Notion page artifact or sync state could not be persisted") {
            syncedPageService.persist(runId, connection.projectId, command)
        }
    }

    private suspend fun <T> step(message: String, operation: suspend () -> T): T {
        return try {
            operation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: RuntimeException) {
            throw NotionPageSyncException(message, exception)
        }
    }
}

/** Identifies a page-local failure that must not abort processing of other workspace pages. */
internal class NotionPageSyncException(
    message: String,
    cause: RuntimeException,
) : RuntimeException(message, cause)
