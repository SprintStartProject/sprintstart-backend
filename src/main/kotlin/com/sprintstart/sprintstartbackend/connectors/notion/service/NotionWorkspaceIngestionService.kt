package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionResourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionFailure
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionOutcome
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionResult
import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Runs one connection-wide sync and reconciles every page visible to its Notion credential.
 *
 * Page failures are recorded independently so one malformed page produces a partial run rather
 * than discarding successful work. Discovery must finish before memberships are changed; pages
 * absent from search are unlinked only after direct lookup confirms removal or exclusion.
 */
@Service
internal class NotionWorkspaceIngestionService(
    private val notionClient: NotionClient,
    private val credentialPersistenceService: NotionCredentialPersistenceService,
    private val connectionPersistenceService: NotionWorkspaceConnectionPersistenceService,
    private val syncedPageService: NotionSyncedPageService,
    private val pageSyncService: NotionPageSyncService,
    private val ingestionApi: NotionArtifactIngestionApi,
) {
    /**
     * Synchronizes all visible pages for one project-owned connection.
     *
     * Set [forceRefresh] for manual runs so every page is fetched and compared by content hash even
     * when Notion reports the same `last_edited_time` as the previous successful sync.
     */
    suspend fun ingest(
        projectId: UUID,
        connectionId: UUID,
        forceRefresh: Boolean = false,
    ): NotionIngestionResult = withContext(Dispatchers.IO) {
        val connection = connectionPersistenceService.requireConnection(projectId, connectionId)
        val runId = UUID.randomUUID()
        if (!connection.sourceEnabled) {
            return@withContext failedResult(runId, connectionId, "Notion source is disabled")
        }
        ingestionApi.startRun(runId, connectionId, connection.workspaceId ?: connectionId.toString())
        try {
            syncWorkspace(runId, connection, forceRefresh)
        } catch (exception: CancellationException) {
            ingestionApi.failRun(runId, "Notion workspace ingestion cancelled")
            throw exception
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            ingestionApi.failRun(runId, "Notion workspace ingestion interrupted")
            throw exception
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            ingestionApi.failRun(runId, "Notion workspace discovery or run persistence failed")
            failedResult(runId, connectionId, "Notion workspace discovery or run persistence failed")
        }
    }

    private suspend fun syncWorkspace(
        runId: UUID,
        connection: NotionWorkspaceConnection,
        forceRefresh: Boolean,
    ): NotionIngestionResult {
        val token = credentialPersistenceService.requireToken(connection.credentialAuthId, connection.credentialName)
        val workspace = notionClient.validateConnection(token)
        credentialPersistenceService.recordTokenIdentity(
            connection.credentialAuthId,
            connection.credentialName,
            workspace,
        )
        val discovered = notionClient.discoverPages(token).associateBy { it.id }
        val states = syncedPageService.findAll(connection.id).associateBy { it.pageId }
        val progress = NotionWorkspaceProgress()

        for (page in discovered.values) {
            processPage(runId, connection, page.id, page.url, progress) {
                pageSyncService.sync(runId, connection, token, page, states[page.id], forceRefresh)
                progress.successful++
            }
        }
        for (state in states.values.filter { it.pageId !in discovered && it.unlinkedAt == null }) {
            processPage(runId, connection, state.pageId, state.pageUrl, progress) {
                val page = findMissingPage(token, state.pageId)
                if (page == null || page.inTrash || page.parent.type in DATA_SOURCE_PARENTS) {
                    syncedPageService.prepareUnlink(connection.id, state.pageId)
                    ingestionApi.unlinkPage(runId, connection.projectId, connection.id, state.pageId)
                    syncedPageService.markUnlinked(connection.id, state.pageId)
                    progress.removed++
                } else {
                    pageSyncService.sync(runId, connection, token, page, state, forceRefresh)
                    progress.successful++
                }
            }
        }
        connectionPersistenceService.recordSuccessfulSync(connection.projectId, connection.id)
        ingestionApi.finishRun(runId, progress.successful + progress.removed)
        return NotionIngestionResult(
            runId,
            connection.id,
            progress.outcome(),
            successfulPages = progress.successful,
            failedPages = progress.failed,
            removedPages = progress.removed,
        )
    }

    private suspend fun findMissingPage(token: String, pageId: String): NotionApiPageResponse? {
        return try {
            notionClient.getPage(token, pageId)
        } catch (_: NotionResourceNotFoundException) {
            null
        }
    }

    private suspend fun processPage(
        runId: UUID,
        connection: NotionWorkspaceConnection,
        pageId: String,
        pageUrl: String,
        progress: NotionWorkspaceProgress,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (@Suppress("SwallowedException") exception: RuntimeException) {
            val reason = if (exception is NotionPageSyncException) {
                exception.message
            } else {
                "Notion page reconciliation failed"
            }
            ingestionApi.recordPageFailure(runId, notionPageSourceId(connection.id, pageId), pageUrl, reason.orEmpty())
            progress.failed++
        }
    }

    private fun failedResult(runId: UUID, connectionId: UUID, message: String): NotionIngestionResult {
        return NotionIngestionResult(
            runId,
            connectionId,
            NotionIngestionOutcome.FAILED,
            NotionIngestionFailure(NotionIngestionFailureStage.FETCHING, message),
        )
    }

    private companion object {
        val DATA_SOURCE_PARENTS = setOf("database_id", "data_source_id")
    }
}

private class NotionWorkspaceProgress {
    var successful: Int = 0
    var failed: Int = 0
    var removed: Int = 0

    fun outcome(): NotionIngestionOutcome {
        return when {
            failed == 0 -> NotionIngestionOutcome.COMPLETED
            successful + removed > 0 -> NotionIngestionOutcome.PARTIAL
            else -> NotionIngestionOutcome.FAILED
        }
    }
}
