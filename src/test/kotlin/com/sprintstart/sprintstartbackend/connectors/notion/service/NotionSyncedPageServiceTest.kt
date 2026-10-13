package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionSyncedPage
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionSyncedPageRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageMetadataCommand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class NotionSyncedPageServiceTest {
    private val pages = mockk<NotionSyncedPageRepository>()
    private val connections = mockk<NotionWorkspaceConnectionRepository>()
    private val api = mockk<NotionArtifactIngestionApi>()
    private val service = NotionSyncedPageService(pages, connections, api)
    private val connection = NotionWorkspaceConnection(
        projectId = UUID.randomUUID(),
        credentialAuthId = "auth",
        credentialName = "credential",
    )
    private val command = NotionPageArtifactCommand(
        sourceId = "notion:${connection.id}:page:page",
        sourceUrl = "https://notion.so/page",
        sourceVersion = "version",
        title = "Page",
        bodyText = "Body",
        lastEditedTime = Instant.parse("2026-01-01T00:00:00Z"),
        metadata = NotionPageMetadataCommand(connection.id, "page", emptyList(), emptyList(), emptyList()),
    )
    private val runId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { connections.findForUpdate(connection.id, connection.projectId) } returns connection
        every { api.persistPage(runId, connection.projectId, command) } returns
            NotionArtifactWriteResult(NotionArtifactWriteOutcome.UPDATED, "new-hash")
        every { pages.saveAndFlush(any()) } answers { firstArg() }
    }

    @Test
    fun `successful write reuses page state and clears unlink marker`() {
        val state = NotionSyncedPage(
            connectionId = connection.id,
            pageId = "page",
            pageTitle = "old",
            pageUrl = "old",
            unlinkedAt = Instant.now(),
        )
        every { pages.findByConnectionIdAndPageId(connection.id, "page") } returns state
        service.persist(runId, connection.projectId, command)
        assertThat(state.contentHash).isEqualTo("new-hash")
        assertThat(state.lastEditedTime).isEqualTo(command.lastEditedTime)
        assertThat(state.pageTitle).isEqualTo("Page")
        assertThat(state.unlinkedAt).isNull()
        verify { pages.saveAndFlush(state) }
    }

    @Test
    fun `deleted connection cannot write artifacts back into a project`() {
        every { connections.findForUpdate(connection.id, connection.projectId) } returns null
        assertThrows<NotionWorkspaceConnectionNotFoundException> {
            service.persist(runId, connection.projectId, command)
        }
        verify(exactly = 0) { api.persistPage(any(), any(), any()) }
    }

    @Test
    fun `failed artifact write never advances page sync state`() {
        every { api.persistPage(any(), any(), any()) } throws IllegalStateException("failure")
        assertThrows<IllegalStateException> { service.persist(runId, connection.projectId, command) }
        verify(exactly = 0) { pages.saveAndFlush(any()) }
    }

    @Test
    fun `pending unlink invalidates shortcut before attempting external index update`() {
        val state = NotionSyncedPage(
            connectionId = connection.id,
            pageId = "page",
            pageTitle = "Page",
            pageUrl = "url",
            contentHash = "hash",
        )
        every { pages.findByConnectionIdAndPageId(connection.id, "page") } returns state
        service.prepareUnlink(connection.id, "page")
        assertThat(state.contentHash).isNull()
        assertThat(state.unlinkedAt).isNull()
        service.markUnlinked(connection.id, "page")
        assertThat(state.unlinkedAt).isNotNull()
    }
}
