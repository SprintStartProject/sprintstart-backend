package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactFailure
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageMetadataCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceSourceAclCommand
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.UUID

class ConfluenceArtifactBatchServiceTest {
    private val itemPersistenceService = mockk<ConfluenceArtifactItemPersistenceService>()
    private val failurePersistenceService = mockk<ConfluenceArtifactFailurePersistenceService>()
    private val service = ConfluenceArtifactBatchService(itemPersistenceService, failurePersistenceService)

    private val runId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @Test
    fun `counts every outcome and records the failures the pages brought along`() {
        val created = page("page-1")
        val updated = page("page-2")
        val unchanged = page("page-3")
        val clash = page("page-4")
        val earlier = ConfluencePageArtifactFailure(
            pageId = "page-5",
            sourceUrl = null,
            stage = ConfluenceArtifactFailureStage.PARSING,
            reason = "unreadable",
        )
        every { itemPersistenceService.persist(runId, projectId, created) } returns
            ConfluenceArtifactItemPersistenceResult.CREATED
        every { itemPersistenceService.persist(runId, projectId, updated) } returns
            ConfluenceArtifactItemPersistenceResult.UPDATED
        every { itemPersistenceService.persist(runId, projectId, unchanged) } returns
            ConfluenceArtifactItemPersistenceResult.UNCHANGED
        every { itemPersistenceService.persist(runId, projectId, clash) } throws
            DataIntegrityViolationException("duplicate")
        every { failurePersistenceService.record(any(), any()) } just Runs

        val pages = listOf(created, updated, unchanged, clash)

        val result = service.persist(ConfluenceArtifactBatchCommand(runId, projectId, pages, listOf(earlier)))

        val persistenceFailure = ConfluencePageArtifactFailure(
            pageId = "page-4",
            sourceUrl = "https://example.atlassian.net/wiki/page-4",
            stage = ConfluenceArtifactFailureStage.PERSISTENCE,
            reason = "Confluence page artifact could not be persisted",
        )
        assertThat(result.created).isEqualTo(1)
        assertThat(result.updated).isEqualTo(1)
        assertThat(result.unchanged).isEqualTo(1)
        assertThat(result.failed).isEqualTo(2)
        assertThat(result.persistenceFailures).containsExactly(persistenceFailure)
        verify(exactly = 1) { failurePersistenceService.record(runId, earlier) }
        verify(exactly = 1) { failurePersistenceService.record(runId, persistenceFailure) }
    }

    @Test
    fun `a clean batch records no failures`() {
        val only = page("page-1")
        every { itemPersistenceService.persist(runId, projectId, only) } returns
            ConfluenceArtifactItemPersistenceResult.CREATED

        val result = service.persist(ConfluenceArtifactBatchCommand(runId, projectId, listOf(only)))

        assertThat(result.created).isEqualTo(1)
        assertThat(result.failed).isZero()
        assertThat(result.persistenceFailures).isEmpty()
        verify(exactly = 0) { failurePersistenceService.record(any(), any()) }
    }

    @Test
    fun `rejects a batch that names the same page twice`() {
        val first = page("page-1")

        assertThatThrownBy {
            service.persist(ConfluenceArtifactBatchCommand(runId, projectId, listOf(first, first)))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Confluence ingestion batch contains duplicate source identities")
        verify(exactly = 0) { itemPersistenceService.persist(any(), any(), any()) }
    }

    private fun page(pageId: String): ConfluencePageArtifactCommand = ConfluencePageArtifactCommand(
        sourceId = "confluence:connection-1:page:$pageId",
        sourceUrl = "https://example.atlassian.net/wiki/$pageId",
        sourceVersion = "3",
        title = "Page $pageId",
        bodyText = "body",
        versionCreatedAt = Instant.parse("2026-09-27T10:00:00Z"),
        metadata = ConfluencePageMetadataCommand(
            connectionId = UUID.randomUUID(),
            tenantBaseUrl = "https://example.atlassian.net",
            spaceId = "space-1",
            spaceKey = "ENG",
            pageId = pageId,
            versionNumber = 3,
            versionCreatedAt = Instant.parse("2026-09-27T10:00:00Z"),
            parentId = null,
            parentType = null,
            webUiPath = null,
            sections = emptyList(),
            tables = emptyList(),
            codeBlocks = emptyList(),
            relationships = emptyList(),
            sourceAcl = ConfluenceSourceAclCommand(emptyList(), emptyList()),
        ),
    )
}
