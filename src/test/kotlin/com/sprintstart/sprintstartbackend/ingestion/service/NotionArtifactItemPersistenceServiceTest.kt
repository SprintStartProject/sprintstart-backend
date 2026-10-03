package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionCodeBlockCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageMetadataCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionSectionCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.NotionArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class NotionArtifactItemPersistenceServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val metadataJsonMapper = mockk<ArtifactMetadataJsonMapper>()
    private val service = NotionArtifactItemPersistenceService(
        ingestionRunRepository,
        artifactRepository,
        metadataJsonMapper,
    )

    @Test
    fun `creates then leaves unchanged then updates the same canonical artifact`() {
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.NOTION,
            status = IngestionRunStatus.RUNNING,
        )
        val projectId = UUID.randomUUID()
        val command = command()
        val artifactSlot = slot<Artifact>()
        val metadataSlot = slot<ArtifactMetadata>()
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.of(run)
        every { metadataJsonMapper.toJson(capture(metadataSlot)) } returns "{\"notionPageId\":\"page-1\"}"
        every { artifactRepository.findBySourceSystemAndSourceId(SourceSystem.NOTION, command.sourceId) } returns null
        every { artifactRepository.saveAndFlush(capture(artifactSlot)) } answers { firstArg() }
        every { artifactRepository.flush() } just Runs

        val created = service.persist(run.id, projectId, command)
        val artifact = artifactSlot.captured
        every {
            artifactRepository.findBySourceSystemAndSourceId(SourceSystem.NOTION, command.sourceId)
        } returns artifact
        val unchanged = service.persist(run.id, projectId, command)
        val updated = service.persist(run.id, projectId, command.copy(bodyText = "changed body"))

        assertThat(created.outcome).isEqualTo(NotionArtifactWriteOutcome.CREATED)
        assertThat(created.contentHash).hasSize(64)
        assertThat(unchanged.outcome).isEqualTo(NotionArtifactWriteOutcome.UNCHANGED)
        assertThat(unchanged.contentHash).isEqualTo(created.contentHash)
        assertThat(updated.outcome).isEqualTo(NotionArtifactWriteOutcome.UPDATED)
        assertThat(updated.contentHash).isNotEqualTo(created.contentHash)
        assertThat(artifact.sourceSystem).isEqualTo(SourceSystem.NOTION)
        assertThat(artifact.sourceId).isEqualTo(command.sourceId)
        assertThat(artifact.content).isEqualTo("changed body")
        assertThat(artifact.hash).isEqualTo(updated.contentHash)
        assertThat(artifact.projectIds).containsExactly(projectId)
        assertThat(run.ingestedCount).isEqualTo(1)
        assertThat(run.updatedCount).isEqualTo(1)
        assertThat(metadataSlot.captured).isInstanceOf(NotionArtifactMetadata::class.java)
    }

    private fun command(): NotionPageArtifactCommand {
        return NotionPageArtifactCommand(
            sourceId = "notion:connection-1:page:page-1",
            sourceUrl = "https://www.notion.so/page-1",
            sourceVersion = "2026-09-27T10:00:00Z",
            title = "Sprint Start",
            bodyText = "body",
            lastEditedTime = Instant.parse("2026-09-27T10:00:00Z"),
            metadata = NotionPageMetadataCommand(
                connectionId = UUID.randomUUID(),
                pageId = "page-1",
                sections = listOf(NotionSectionCommand("Heading", 1)),
                tables = listOf("| A |"),
                codeBlocks = listOf(NotionCodeBlockCommand("kotlin", "println(1)")),
            ),
        )
    }
}
