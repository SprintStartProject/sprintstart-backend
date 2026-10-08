package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionBlockParser
import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionBody
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiParentResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionResourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTokenIdentity
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionSyncedPage
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class NotionWorkspaceIngestionServiceTest {
    private val client = mockk<NotionClient>()
    private val parser = mockk<NotionBlockParser>()
    private val credentials = mockk<NotionCredentialPersistenceService>(relaxUnitFun = true)
    private val connections = mockk<NotionWorkspaceConnectionPersistenceService>(relaxUnitFun = true)
    private val states = mockk<NotionSyncedPageService>(relaxUnitFun = true)
    private val api = mockk<NotionArtifactIngestionApi>(relaxUnitFun = true)
    private val connection = NotionWorkspaceConnection(
        projectId = UUID.randomUUID(),
        credentialAuthId = "auth-id",
        credentialName = "team",
        workspaceId = "workspace",
        workspaceName = "Engineering",
        tokenOwnerId = "bot",
    )
    private val service = NotionWorkspaceIngestionService(
        client,
        credentials,
        connections,
        states,
        NotionPageSyncService(client, parser, NotionPageArtifactMapper(), states),
        api,
    )

    @BeforeEach
    fun setUp() {
        every { connections.requireConnection(connection.projectId, connection.id) } returns connection
        every { credentials.requireToken("auth-id", "team") } returns "token"
        coEvery { client.validateConnection("token") } returns
            NotionTokenIdentity("bot", "workspace", "Engineering")
        coEvery { client.discoverPages("token") } returns listOf(page("parent"), page("child"))
        every { states.findAll(connection.id) } returns emptyList()
        coEvery { client.getBlockTree("token", any()) } returns emptyList()
        every { parser.parse(emptyList()) } returns ParsedNotionBody("body")
        every { states.persist(any(), connection.projectId, any()) } returns
            NotionArtifactWriteResult(NotionArtifactWriteOutcome.CREATED, "hash")
    }

    @Test
    fun `one run ingests parent and subpage as separate artifacts`() = runTest {
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.COMPLETED)
        assertThat(result.successfulPages).isEqualTo(2)
        verify(exactly = 1) { api.startRun(result.runId, connection.id, "workspace") }
        verify(
            exactly = 1,
        ) { states.persist(result.runId, connection.projectId, match { it.metadata.pageId == "parent" }) }
        verify(
            exactly = 1,
        ) { states.persist(result.runId, connection.projectId, match { it.metadata.pageId == "child" }) }
        verify { api.finishRun(result.runId, 2) }
    }

    @Test
    fun `matching timestamp skips only the unchanged page`() = runTest {
        every { states.findAll(connection.id) } returns listOf(state("parent"))
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.successfulPages).isEqualTo(2)
        coVerify(exactly = 0) { client.getBlockTree("token", "parent") }
        coVerify(exactly = 1) { client.getBlockTree("token", "child") }
    }

    @Test
    fun `manual refresh checks content despite unchanged timestamp`() = runTest {
        every { states.findAll(connection.id) } returns listOf(state("parent"))
        service.ingest(connection.projectId, connection.id, forceRefresh = true)
        coVerify(exactly = 1) { client.getBlockTree("token", "parent") }
        verify { states.persist(any(), connection.projectId, match { it.metadata.pageId == "parent" }) }
    }

    @Test
    fun `one fetch failure is recorded and other pages still sync`() = runTest {
        coEvery { client.getBlockTree("token", "parent") } throws IllegalStateException("sensitive detail")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.PARTIAL)
        assertThat(result.failedPages).isEqualTo(1)
        assertThat(result.successfulPages).isEqualTo(1)
        verify {
            api.recordPageFailure(
                result.runId,
                notionPageSourceId(connection.id, "parent"),
                any(),
                "Notion page content could not be fetched",
            )
        }
        verify { api.finishRun(result.runId, 1) }
        verify(exactly = 0) { api.failRun(any(), any()) }
    }

    @Test
    fun `parser failures record safe reasons without saving state`() = runTest {
        every { parser.parse(any()) } throws IllegalStateException("sensitive content")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.FAILED)
        assertThat(result.failedPages).isEqualTo(2)
        verify(exactly = 2) { api.recordPageFailure(any(), any(), any(), "Notion page blocks could not be parsed") }
        verify(exactly = 0) { states.persist(any(), any(), any()) }
    }

    @Test
    fun `persistence failure does not abort other pages`() = runTest {
        every { states.persist(any(), any(), match { it.metadata.pageId == "parent" }) } throws
            IllegalStateException("database detail")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.PARTIAL)
        assertThat(result.successfulPages).isEqualTo(1)
    }

    @Test
    fun `search omission alone does not unlink an accessible page`() = runTest {
        every { states.findAll(connection.id) } returns listOf(state("missing"))
        coEvery { client.getPage("token", "missing") } returns page("missing")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.successfulPages).isEqualTo(3)
        coVerify(exactly = 0) { api.unlinkPage(any(), any(), any(), any()) }
    }

    @Test
    fun `confirmed revoked page is unlinked and marked for reingestion if shared again`() = runTest {
        every { states.findAll(connection.id) } returns listOf(state("revoked"))
        coEvery { client.getPage("token", "revoked") } throws NotionResourceNotFoundException("retrieving page")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.removedPages).isEqualTo(1)
        coVerify { api.unlinkPage(result.runId, connection.projectId, connection.id, "revoked") }
        verify { states.markUnlinked(connection.id, "revoked") }
    }

    @Test
    fun `reshared page bypasses timestamp shortcut`() = runTest {
        every { states.findAll(connection.id) } returns listOf(state("parent").apply { unlinkedAt = Instant.now() })
        service.ingest(connection.projectId, connection.id)
        coVerify(exactly = 1) { client.getBlockTree("token", "parent") }
    }

    @Test
    fun `failed discovery never reconciles memberships`() = runTest {
        coEvery { client.discoverPages("token") } throws IllegalStateException("failed cursor batch")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.FAILED)
        coVerify(exactly = 0) { api.unlinkPage(any(), any(), any(), any()) }
        verify(exactly = 0) { states.persist(any(), any(), any()) }
        verify { api.failRun(result.runId, any()) }
    }

    @Test
    fun `transient failure checking missing page preserves membership`() = runTest {
        every { states.findAll(connection.id) } returns listOf(state("missing"))
        coEvery { client.getPage("token", "missing") } throws IllegalStateException("temporary failure")
        val result = service.ingest(connection.projectId, connection.id)
        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.PARTIAL)
        coVerify(exactly = 0) { api.unlinkPage(any(), any(), any(), any()) }
    }

    @Test
    fun `cancellation propagates instead of becoming a failed page`() = runTest {
        coEvery { client.getBlockTree("token", "parent") } throws CancellationException()
        assertThrows<CancellationException> { service.ingest(connection.projectId, connection.id) }
        verify(exactly = 0) { api.recordPageFailure(any(), any(), any(), any()) }
        verify { api.failRun(any(), any()) }
    }

    private fun state(pageId: String): NotionSyncedPage {
        return NotionSyncedPage(
            connectionId = connection.id,
            pageId = pageId,
            pageTitle = pageId,
            pageUrl = "https://notion.so/$pageId",
            lastEditedTime = Instant.parse("2026-09-27T10:00:00Z"),
            contentHash = "hash",
        )
    }

    private fun page(id: String): NotionApiPageResponse {
        return NotionApiPageResponse(
            id = id,
            url = "https://notion.so/$id",
            lastEditedTime = "2026-09-27T10:00:00Z",
            inTrash = false,
            parent = NotionApiParentResponse(type = "workspace", workspace = true),
            properties = emptyMap(),
        )
    }
}
