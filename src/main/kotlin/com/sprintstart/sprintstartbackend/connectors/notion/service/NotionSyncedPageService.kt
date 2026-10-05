package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionSyncedPage
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionSyncedPageRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Keeps per-page progress separate from a connection's schedule and run history.
 *
 * Artifact persistence and sync-state updates share one transaction. Connection locking prevents
 * a concurrent connection deletion from completing while a page write is in progress.
 */
@Service
internal class NotionSyncedPageService(
    private val pageRepository: NotionSyncedPageRepository,
    private val connectionRepository: NotionWorkspaceConnectionRepository,
    private val ingestionApi: NotionArtifactIngestionApi,
) {
    @Transactional(readOnly = true)
    fun findAll(connectionId: UUID): List<NotionSyncedPage> {
        return pageRepository.findAllByConnectionId(connectionId)
    }

    /** Locks the connection so deletion cannot race a canonical artifact write. */
    @Transactional
    fun persist(runId: UUID, projectId: UUID, command: NotionPageArtifactCommand): NotionArtifactWriteResult {
        val connectionId = command.metadata.connectionId
        connectionRepository.findForUpdate(connectionId, projectId)
            ?: throw NotionWorkspaceConnectionNotFoundException(connectionId, projectId)
        val result = ingestionApi.persistPage(runId, projectId, command)
        val state = pageRepository.findByConnectionIdAndPageId(connectionId, command.metadata.pageId)
            ?: NotionSyncedPage(
                connectionId = connectionId,
                pageId = command.metadata.pageId,
                pageTitle = command.title,
                pageUrl = command.sourceUrl,
            )
        state.pageTitle = command.title
        state.pageUrl = command.sourceUrl
        state.lastEditedTime = command.lastEditedTime
        state.contentHash = result.contentHash
        state.lastSyncedAt = Instant.now()
        state.unlinkedAt = null
        pageRepository.saveAndFlush(state)
        return result
    }

    /** Invalidates the hash before unlinking so a failed external unlink remains retryable. */
    @Transactional
    fun prepareUnlink(connectionId: UUID, pageId: String) {
        pageRepository.findByConnectionIdAndPageId(connectionId, pageId)?.contentHash = null
    }

    /** Records a successfully unlinked page while retaining its stable remote identity. */
    @Transactional
    fun markUnlinked(connectionId: UUID, pageId: String) {
        pageRepository.findByConnectionIdAndPageId(connectionId, pageId)?.unlinkedAt = Instant.now()
    }
}
