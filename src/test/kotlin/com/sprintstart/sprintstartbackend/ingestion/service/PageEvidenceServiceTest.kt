package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageEditCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.PageEdit
import com.sprintstart.sprintstartbackend.ingestion.repository.PageEditRepository
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PageEvidenceServiceTest {
    private val edits = mockk<PageEditRepository>(relaxed = true)
    private val complexity = mockk<FileComplexityService>(relaxed = true)
    private val service = PageEvidenceService(edits, complexity)

    private val run =
        IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.CONFLUENCE,
            status = IngestionRunStatus.COMPLETED,
        )
    private val page = Artifact(
        sourceSystem = SourceSystem.CONFLUENCE,
        sourceId = "confluence:c:page:1",
        sourceUrl = null,
        artifactType = ArtifactType.PAGE,
        title = "Setup",
        content = "Run the app",
        mime = null,
        language = null,
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = run,
        hash = null,
    )
    private val versions = listOf(
        ConfluencePageEditCommand(1, "ada", Instant.parse("2026-01-01T00:00:00Z")),
        ConfluencePageEditCommand(2, "bob", Instant.parse("2026-02-01T00:00:00Z")),
    )

    @Test
    fun `the versions of a page replace the ones recorded before`() {
        val saved = slot<List<PageEdit>>()
        every { edits.saveAll(capture(saved)) } answers { saved.captured }

        service.record(page, "confluence:ENG", "Setup", versions)

        verifyOrder {
            edits.deleteAllByArtifactId(page.id)
            edits.saveAll(any<List<PageEdit>>())
        }
        assertThat(saved.captured.map { Triple(it.versionNumber, it.authorAccountId, it.repository) })
            .containsExactly(Triple(1, "ada", "confluence:ENG"), Triple(2, "bob", "confluence:ENG"))
        assertThat(saved.captured.map { it.artifact }).containsOnly(page)
    }

    @Test
    fun `a page whose versions could not be read keeps its earlier history`() {
        service.record(page, "confluence:ENG", "Setup", null)

        verify(exactly = 0) { edits.deleteAllByArtifactId(any()) }
        verify(exactly = 0) { edits.saveAll(any<List<PageEdit>>()) }
    }

    @Test
    fun `a page is measured by its words under its title`() {
        service.record(page, "confluence:ENG", "Setup", null)

        verify { complexity.measure("confluence:ENG", "Setup", "Run the app", FileKind.PROSE) }
        verify(exactly = 0) { complexity.forget(any(), any()) }
    }

    @Test
    fun `a renamed page forgets the measurement under its old title`() {
        service.record(page, "confluence:ENG", "Old setup", null)

        verify { complexity.forget("confluence:ENG", "Old setup") }
        verify { complexity.measure("confluence:ENG", "Setup", "Run the app", FileKind.PROSE) }
    }
}
