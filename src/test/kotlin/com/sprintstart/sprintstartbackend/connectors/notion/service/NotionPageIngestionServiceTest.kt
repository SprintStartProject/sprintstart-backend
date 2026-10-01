package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionBlockParser
import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionBody
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPagePropertyResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiParentResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class NotionPageIngestionServiceTest {
    private val notionClient = mockk<NotionClient>()
    private val parser = mockk<NotionBlockParser>()
    private val credentialPersistenceService = mockk<NotionCredentialPersistenceService>()
    private val connectionPersistenceService = mockk<NotionPageConnectionPersistenceService>()
    private val ingestionApi = mockk<NotionArtifactIngestionApi>()
    private val connection = connection()
    private val projectId = connection.projectId
    private val service = NotionPageIngestionService(
        notionClient,
        parser,
        credentialPersistenceService,
        connectionPersistenceService,
        NotionPageArtifactMapper(),
        ingestionApi,
    )

    @BeforeEach
    fun setUp() {
        every { connectionPersistenceService.requireConnection(projectId, connection.id) } returns connection
        every { credentialPersistenceService.requireToken("auth-id", "team-token") } returns "secret-token"
        every { ingestionApi.startRun(any(), connection.id, connection.pageUrl) } just Runs
        every { ingestionApi.finishRun(any(), any()) } just Runs
        every { ingestionApi.failRun(any(), any()) } just Runs
        every { connectionPersistenceService.recordSuccessfulSync(any(), any(), any(), any(), any(), any()) } just Runs
        coEvery { notionClient.getPage("secret-token", "page-1") } returns page()
        coEvery { notionClient.getBlockTree("secret-token", "page-1") } returns emptyList()
        every { parser.parse(emptyList()) } returns ParsedNotionBody("body")
        every { ingestionApi.persistPage(any(), projectId, any()) } returns
            NotionArtifactWriteResult(NotionArtifactWriteOutcome.CREATED, "a".repeat(64))
    }

    @Test
    fun `persists canonical page updates sync state and finishes run`() = runTest {
        val result = service.ingest(projectId, connection.id)

        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.CREATED)
        assertThat(result.failure).isNull()
        verify(exactly = 1) { ingestionApi.startRun(result.runId, connection.id, connection.pageUrl) }
        verify(exactly = 1) { ingestionApi.persistPage(result.runId, projectId, any()) }
        verify(exactly = 1) {
            connectionPersistenceService.recordSuccessfulSync(
                projectId,
                connection.id,
                "Sprint Start",
                "https://www.notion.so/page-1",
                Instant.parse("2026-09-27T10:00:00Z"),
                "a".repeat(64),
            )
        }
        verify(exactly = 1) { ingestionApi.finishRun(result.runId, 1) }
        verify(exactly = 0) { ingestionApi.failRun(any(), any()) }
    }

    @Test
    fun `matching edit time and stored hash skips block loading and artifact write`() = runTest {
        connection.lastEditedTime = Instant.parse("2026-09-27T10:00:00Z")
        connection.contentHash = "b".repeat(64)

        val result = service.ingest(projectId, connection.id)

        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.UNCHANGED)
        coVerify(exactly = 0) { notionClient.getBlockTree(any(), any()) }
        verify(exactly = 0) { parser.parse(any()) }
        verify(exactly = 0) { ingestionApi.persistPage(any(), any(), any()) }
        verify(exactly = 1) { ingestionApi.finishRun(result.runId, 1) }
    }

    @Test
    fun `client failure returns fetching failure and marks run failed`() = runTest {
        coEvery { notionClient.getPage(any(), any()) } throws NotionAuthenticationException("retrieving page")

        val result = service.ingest(projectId, connection.id)

        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.FAILED)
        assertThat(result.failure?.stage).isEqualTo(NotionIngestionFailureStage.FETCHING)
        assertThat(result.failure?.message).isEqualTo("Notion page content could not be fetched")
        verify(exactly = 1) { ingestionApi.failRun(result.runId, any()) }
        verify(exactly = 0) { ingestionApi.persistPage(any(), any(), any()) }
    }

    @Test
    fun `parser failure returns safe parsing failure and marks run failed`() = runTest {
        every { parser.parse(any()) } throws IllegalStateException("sensitive block content")

        val result = service.ingest(projectId, connection.id)

        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.FAILED)
        assertThat(result.failure?.stage).isEqualTo(NotionIngestionFailureStage.PARSING)
        assertThat(result.failure?.message)
            .isEqualTo("Notion page blocks could not be parsed")
            .doesNotContain("sensitive block content")
        verify(exactly = 1) { ingestionApi.failRun(result.runId, any()) }
        verify(exactly = 0) { ingestionApi.persistPage(any(), any(), any()) }
    }

    @Test
    fun `artifact write failure returns persistence failure and marks run failed`() = runTest {
        every { ingestionApi.persistPage(any(), any(), any()) } throws IllegalStateException("database detail")

        val result = service.ingest(projectId, connection.id)

        assertThat(result.outcome).isEqualTo(NotionIngestionOutcome.FAILED)
        assertThat(result.failure?.stage).isEqualTo(NotionIngestionFailureStage.PERSISTENCE)
        assertThat(result.failure?.message)
            .isEqualTo("Notion page artifact could not be persisted")
            .doesNotContain("database detail")
        verify(exactly = 1) { ingestionApi.failRun(result.runId, any()) }
        verify(exactly = 0) {
            connectionPersistenceService.recordSuccessfulSync(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    private fun connection(): NotionPageConnection {
        return NotionPageConnection(
            id = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            pageId = "page-1",
            pageTitle = "Stored title",
            pageUrl = "https://www.notion.so/page-1",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
    }

    private fun page(): NotionApiPageResponse {
        return NotionApiPageResponse(
            id = "page-1",
            url = "https://www.notion.so/page-1",
            lastEditedTime = "2026-09-27T10:00:00Z",
            inTrash = false,
            parent = NotionApiParentResponse(type = "workspace", workspace = true),
            properties = mapOf(
                "Name" to NotionApiPagePropertyResponse(
                    type = "title",
                    title = listOf(NotionRichText("Sprint Start")),
                ),
            ),
        )
    }
}
