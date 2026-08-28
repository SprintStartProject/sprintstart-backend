package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageBatchResult
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageFetchStage
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageRestrictions
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageVersion
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceStorageBody
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionStatus
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ConfluenceStorageFormatParser
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ParsedConfluenceBody
import com.sprintstart.sprintstartbackend.ingestion.external.ConfluenceArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfluencePageIngestionServiceTest {
    private val connectionService = mockk<ConfluenceConnectionRuntimeService>()
    private val client = mockk<ConfluenceClient>()
    private val ingestionApi = mockk<ConfluenceArtifactIngestionApi>()
    private val connection = connection()
    private val projectId = connection.projectId
    private val batchSlot = slot<ConfluenceArtifactBatchCommand>()

    @BeforeEach
    fun setUp() {
        every { connectionService.getConnectionForIngestion(projectId, connection.id) } returns connection
        every { ingestionApi.startRun(any(), connection.id, any()) } just Runs
        every { ingestionApi.finishRun(any(), any()) } just Runs
        every { ingestionApi.failRun(any(), any()) } just Runs
    }

    @Test
    fun `ingests eligible pages with parsed content hierarchy and restriction failure`() = runTest {
        val parser = ConfluenceStorageFormatParser()
        val service = service(parser)
        val root = page("100", null, "<h2>Deploy</h2><p>Use Kubernetes.</p>")
        val child = page(
            "200",
            "100",
            "<table><tr><th>Env</th></tr><tr><td>prod</td></tr></table>" +
                "<ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"language\">bash</ac:parameter>" +
                "<ac:plain-text-body>kubectl apply</ac:plain-text-body></ac:structured-macro>",
        )
        val deniedChild = page("300", "100", "<p>Denied body</p>")
        val scopedConnection = connection.copyWithFilters(denylist = listOf("300"))
        every {
            connectionService.getConnectionForIngestion(projectId, connection.id)
        } returns scopedConnection
        coEvery { client.getPages(any(), any(), any()) } returns ConfluencePageBatchResult(
            successfulPages = listOf(root, child, deniedChild),
            failures = listOf(
                ConfluencePageFailure(
                    pageId = "400",
                    stage = ConfluencePageFetchStage.RESTRICTIONS,
                    httpStatus = 404,
                    attempts = 1,
                    message = "safe client message",
                ),
            ),
        )
        every { ingestionApi.persistBatch(capture(batchSlot)) } returns
            ConfluenceArtifactBatchResult(created = 2, updated = 0, unchanged = 0, failed = 1)

        val result = service.ingest(projectId, connection.id)

        val commands = batchSlot.captured.artifacts.associateBy { command -> command.metadata.pageId }
        val rootCommand = commands.getValue("100")
        val childCommand = commands.getValue("200")
        val rootHeading = rootCommand
            .metadata
            .sections
            .single()
            .heading
        val childRelationshipType = childCommand
            .metadata
            .relationships
            .single()
            .type
        val childCode = childCommand
            .metadata
            .codeBlocks
            .single()
            .code
        assertThat(commands).containsOnlyKeys("100", "200")
        assertThat(rootCommand.bodyText).isEqualTo("Deploy\nUse Kubernetes.")
        assertThat(rootHeading).isEqualTo("Deploy")
        assertThat(rootCommand.metadata.relationships.map { it.targetSourceArtifactId })
            .containsExactly("200", "300")
        assertThat(childRelationshipType).isEqualTo(ConfluenceRelationshipType.CHILD_OF)
        assertThat(childCommand.metadata.tables.single()).contains("| prod |")
        assertThat(childCode).isEqualTo("kubectl apply")
        val restrictionFailure = batchSlot.captured.failures.single()
        assertThat(restrictionFailure.pageId).isEqualTo("400")
        assertThat(result.discovered).isEqualTo(4)
        assertThat(result.eligible).isEqualTo(2)
        assertThat(result.filtered).isEqualTo(1)
        assertThat(result.status).isEqualTo(ConfluenceIngestionStatus.PARTIAL)
        assertThat(result.failures.single().httpStatus).isEqualTo(404)
        assertThat(result.failures.single().attempts).isEqualTo(1)
        verify(exactly = 1) { ingestionApi.finishRun(any(), 2) }
    }

    @Test
    fun `allowlist and denylist filter before parsing with denylist winning`() = runTest {
        val parser = mockk<ConfluenceStorageFormatParser>()
        val scopedConnection = connection.copyWithFilters(
            allowlist = listOf("100", "200"),
            denylist = listOf("200"),
        )
        every {
            connectionService.getConnectionForIngestion(projectId, connection.id)
        } returns scopedConnection
        coEvery { client.getPages(any(), any(), any()) } returns ConfluencePageBatchResult(
            successfulPages = listOf(
                page("100", null, "allowed"),
                page("200", null, "denied"),
                page("300", null, "other"),
            ),
            failures = emptyList(),
        )
        every { parser.parse("allowed") } returns ParsedConfluenceBody("allowed")
        every { ingestionApi.persistBatch(capture(batchSlot)) } returns
            ConfluenceArtifactBatchResult(1, 0, 0, 0)

        val result = service(parser).ingest(projectId, connection.id)

        verify(exactly = 1) { parser.parse("allowed") }
        verify(exactly = 0) { parser.parse("denied") }
        verify(exactly = 0) { parser.parse("other") }
        val artifact = batchSlot.captured.artifacts.single()
        assertThat(artifact.metadata.pageId).isEqualTo("100")
        assertThat(result.filtered).isEqualTo(2)
    }

    @Test
    fun `parser failure is reported and never persisted as empty artifact`() = runTest {
        val parser = mockk<ConfluenceStorageFormatParser>()
        coEvery { client.getPages(any(), any(), any()) } returns ConfluencePageBatchResult(
            successfulPages = listOf(page("100", null, "sensitive body")),
            failures = emptyList(),
        )
        every { parser.parse(any()) } throws IllegalStateException("raw sensitive parser detail")
        every { ingestionApi.persistBatch(capture(batchSlot)) } returns
            ConfluenceArtifactBatchResult(0, 0, 0, 1)

        val result = service(parser).ingest(projectId, connection.id)

        assertThat(batchSlot.captured.artifacts).isEmpty()
        val failure = batchSlot.captured.failures.single()
        assertThat(failure.reason)
            .isEqualTo("Confluence page storage format could not be parsed")
            .doesNotContain("sensitive body", "raw sensitive parser detail")
        val resultFailure = result.failures.single()
        assertThat(resultFailure.stage).isEqualTo(ConfluenceIngestionFailureStage.PARSING)
        assertThat(result.status).isEqualTo(ConfluenceIngestionStatus.FAILED)
        verify(exactly = 1) { ingestionApi.finishRun(any(), 0) }
    }

    @Test
    fun `mapping failure is recorded once without retrying or persisting the page`() = runTest {
        val mapper = mockk<ConfluencePageArtifactMapper>()
        coEvery { client.getPages(any(), any(), any()) } returns ConfluencePageBatchResult(
            successfulPages = listOf(page("100", null, "body")),
            failures = emptyList(),
        )
        every { mapper.toCommand(any(), any(), any(), any()) } throws
            IllegalStateException("sensitive mapping detail")
        every { ingestionApi.persistBatch(capture(batchSlot)) } returns
            ConfluenceArtifactBatchResult(0, 0, 0, 1)

        val result = service(ConfluenceStorageFormatParser(), mapper).ingest(projectId, connection.id)

        assertThat(batchSlot.captured.artifacts).isEmpty()
        val persistedStage = batchSlot.captured
            .failures
            .single()
            .stage
        assertThat(persistedStage).isEqualTo(
            com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactFailureStage.MAPPING,
        )
        assertThat(result.failures.single().stage).isEqualTo(ConfluenceIngestionFailureStage.MAPPING)
        assertThat(result.failures.single().message)
            .isEqualTo("Confluence page could not be mapped to an artifact")
            .doesNotContain("sensitive mapping detail")
        verify(exactly = 1) { mapper.toCommand(any(), any(), any(), any()) }
    }

    @Test
    fun `unchanged page plus failed restriction finishes partial`() = runTest {
        coEvery { client.getPages(any(), any(), any()) } returns ConfluencePageBatchResult(
            successfulPages = listOf(page("100", null, "body")),
            failures = listOf(
                ConfluencePageFailure(
                    pageId = "200",
                    stage = ConfluencePageFetchStage.RESTRICTIONS,
                    httpStatus = 503,
                    attempts = 3,
                    message = "safe client message",
                ),
            ),
        )
        every { ingestionApi.persistBatch(capture(batchSlot)) } returns
            ConfluenceArtifactBatchResult(0, 0, 1, 1)

        val result = service(ConfluenceStorageFormatParser()).ingest(projectId, connection.id)

        assertThat(result.status).isEqualTo(ConfluenceIngestionStatus.PARTIAL)
        assertThat(result.unchanged).isEqualTo(1)
        assertThat(result.failed).isEqualTo(1)
        assertThat(result.failures.single().attempts).isEqualTo(3)
        verify(exactly = 1) { ingestionApi.finishRun(any(), 1) }
    }

    @Test
    fun `self-parent page is a typed failure and is not parsed`() = runTest {
        val parser = mockk<ConfluenceStorageFormatParser>()
        coEvery { client.getPages(any(), any(), any()) } returns ConfluencePageBatchResult(
            successfulPages = listOf(page("100", "100", "body")),
            failures = emptyList(),
        )
        every { ingestionApi.persistBatch(capture(batchSlot)) } returns
            ConfluenceArtifactBatchResult(0, 0, 0, 1)

        service(parser).ingest(projectId, connection.id)

        verify(exactly = 0) { parser.parse(any()) }
        val failure = batchSlot.captured.failures.single()
        assertThat(failure.reason).isEqualTo("Confluence page cannot be its own parent")
    }

    @Test
    fun `terminal client failure marks run failed and performs no artifact persistence`() = runTest {
        val exception = ConfluenceAuthenticationException("retrieving pages for space 42")
        coEvery { client.getPages(any(), any(), any()) } throws exception

        val thrown = runCatching { service(ConfluenceStorageFormatParser()).ingest(projectId, connection.id) }
            .exceptionOrNull()

        assertThat(thrown).isSameAs(exception)
        verify(exactly = 1) { ingestionApi.failRun(any(), "Confluence page ingestion terminated before persistence") }
        verify(exactly = 0) { ingestionApi.persistBatch(any()) }
        verify(exactly = 0) { ingestionApi.finishRun(any(), any()) }
    }

    @Test
    fun `cancellation marks the run failed and propagates unchanged`() = runTest {
        val cancellation = CancellationException("cancel ingestion")
        coEvery { client.getPages(any(), any(), any()) } throws cancellation

        val thrown = runCatching { service(ConfluenceStorageFormatParser()).ingest(projectId, connection.id) }
            .exceptionOrNull()

        assertThat(thrown).isSameAs(cancellation)
        verify(exactly = 1) { ingestionApi.failRun(any(), "Confluence page ingestion terminated before persistence") }
        verify(exactly = 0) { ingestionApi.persistBatch(any()) }
        verify(exactly = 0) { ingestionApi.finishRun(any(), any()) }
    }

    private fun service(
        parser: ConfluenceStorageFormatParser,
        mapper: ConfluencePageArtifactMapper = ConfluencePageArtifactMapper(),
    ) = ConfluencePageIngestionService(
        connectionService,
        client,
        parser,
        mapper,
        ingestionApi,
    )

    private fun connection() = ConfluenceConnectionIngestionSnapshot(
        id = UUID.randomUUID(),
        projectId = UUID.randomUUID(),
        baseUrl = "https://tenant.atlassian.net",
        spaceId = "42",
        spaceKey = "ENG",
        sourceEnabled = true,
        pageAllowlist = emptyList(),
        pageDenylist = emptyList(),
        credentials = ConfluenceClientCredentials("fake-user@example.invalid", "fake-token"),
    )

    private fun ConfluenceConnectionIngestionSnapshot.copyWithFilters(
        allowlist: List<String> = pageAllowlist,
        denylist: List<String> = pageDenylist,
    ) = ConfluenceConnectionIngestionSnapshot(
        id,
        projectId,
        baseUrl,
        spaceId,
        spaceKey,
        sourceEnabled,
        allowlist,
        denylist,
        credentials,
    )

    private fun page(id: String, parentId: String?, storage: String) = ConfluencePage(
        id = id,
        title = "Page $id",
        status = "current",
        spaceId = "42",
        parentId = parentId,
        parentType = parentId?.let { "page" },
        version = ConfluencePageVersion(1, Instant.parse("2026-08-01T10:00:00Z")),
        storage = ConfluenceStorageBody("storage", storage),
        webUiPath = "/wiki/spaces/ENG/pages/$id",
        restrictions = ConfluencePageRestrictions(),
    )
}
