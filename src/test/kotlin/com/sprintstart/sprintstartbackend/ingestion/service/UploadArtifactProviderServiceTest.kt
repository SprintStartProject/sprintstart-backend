package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.UploadArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.service.provider.UploadArtifactProviderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class UploadArtifactProviderServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val service = UploadArtifactProviderService(
        ingestionRunRepository,
        artifactRepository,
    )

    private val runId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val uploadArtifactId = UUID.randomUUID()

    @Test
    fun `persistArtifact saves new uploaded artifact and increments ingested count`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { artifactRepository.findBySourceId(uploadArtifactId.toString()) } returns null
        every { ingestionRunRepository.getReferenceById(runId) } returns run
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }
        every { ingestionRunRepository.incrementIngestedCount(runId) } returns Unit

        service.persistArtifact(uploadCommand())

        assertThat(savedArtifact.captured.sourceSystem).isEqualTo(SourceSystem.UPLOAD)
        assertThat(savedArtifact.captured.sourceId).isEqualTo(uploadArtifactId.toString())
        assertThat(savedArtifact.captured.projectIds).containsExactly(projectId)
        assertThat(savedArtifact.captured.title).isEqualTo("notes.md")
        assertThat(savedArtifact.captured.content).isEqualTo("# Notes")
        assertThat(savedArtifact.captured.hash).isEqualTo("hash")
        assertThat(savedArtifact.captured.ingestionRun).isSameAs(run)
        verify(exactly = 1) { ingestionRunRepository.incrementIngestedCount(runId) }
    }

    @Test
    fun `persistArtifact updates changed upload artifact and increments updated count`() {
        val existing = artifact(hash = "old-hash")
        every { artifactRepository.findBySourceId(uploadArtifactId.toString()) } returns existing
        every { ingestionRunRepository.incrementUpdatedCount(runId) } returns Unit

        service.persistArtifact(uploadCommand(content = "# Updated", hash = "new-hash"))

        assertThat(existing.content).isEqualTo("# Updated")
        assertThat(existing.hash).isEqualTo("new-hash")
        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 1) { ingestionRunRepository.incrementUpdatedCount(runId) }
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `deleteArtifact deletes ingestion artifact and records ingestion artifact id for deindex`() {
        val run = ingestionRun()
        val existing = artifact(hash = "hash")
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId(uploadArtifactId.toString()) } returns existing
        every { artifactRepository.deleteById(existing.id) } returns Unit

        service.deleteArtifact(
            transactionId = runId,
            uploadArtifactId = uploadArtifactId,
        )

        assertThat(run.deletedCount).isEqualTo(1)
        assertThat(run.artifactIdsToDeindex).containsExactly(existing.id.toString())
        verify(exactly = 1) { artifactRepository.deleteById(existing.id) }
    }

    @Test
    fun `deleteArtifact throws when run is missing`() {
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.empty()

        assertThatThrownBy {
            service.deleteArtifact(
                transactionId = runId,
                uploadArtifactId = uploadArtifactId,
            )
        }.isInstanceOf(IngestionRunNotFoundException::class.java)
            .hasMessageContaining(runId.toString())
    }

    private fun uploadCommand(
        content: String = "# Notes",
        hash: String = "hash",
    ) = UploadArtifactCommand(
        ingestionRunId = runId,
        projectId = projectId,
        sourceSystem = SourceSystem.UPLOAD,
        sourceId = uploadArtifactId.toString(),
        artifactType = ArtifactType.FILE,
        title = "notes.md",
        content = content,
        mime = "text/markdown",
        language = "Markdown",
        createdAtSource = null,
        updatedAtSource = null,
        hash = hash,
        metadata = mockk(),
    )

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.UPLOAD,
        status = IngestionRunStatus.RUNNING,
    )

    private fun artifact(hash: String?) = Artifact(
        sourceSystem = SourceSystem.UPLOAD,
        sourceId = uploadArtifactId.toString(),
        sourceUrl = null,
        artifactType = ArtifactType.FILE,
        title = "notes.md",
        content = "# Notes",
        mime = "text/markdown",
        language = "Markdown",
        projectIdsInternal = mutableSetOf(projectId),
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = ingestionRun(),
        hash = hash,
    )
}
