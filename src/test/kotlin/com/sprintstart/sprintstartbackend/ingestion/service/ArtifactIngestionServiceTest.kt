package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class ArtifactIngestionServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val service = ArtifactIngestionService(ingestionRunRepository, artifactRepository)

    private val runId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { artifactRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `ingest saves new artifact and increments ingested count`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("github:owner/repo:FILE:src/main/App.kt") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(artifactCommand())

        assertThat(run.ingestedCount).isEqualTo(1)
        assertThat(run.updatedCount).isZero()
        assertThat(savedArtifact.captured.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(savedArtifact.captured.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(savedArtifact.captured.title).isEqualTo("App.kt")
        assertThat(savedArtifact.captured.bodyText).isEqualTo("content")
        assertThat(savedArtifact.captured.hash).isEqualTo("hash-1")
        assertThat(savedArtifact.captured.ingestionRun).isSameAs(run)
    }

    @Test
    fun `ingest throws when run does not exist`() {
        every { ingestionRunRepository.findById(runId) } returns Optional.empty()

        assertThatThrownBy { service.persistArtifact(artifactCommand()) }
            .isInstanceOf(IngestionRunNotFoundException::class.java)
            .hasMessageContaining(runId.toString())

        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `ingest ignores duplicate commit source id`() {
        val run = ingestionRun()
        val existing = artifact(artifactType = ArtifactType.COMMIT, hash = null)
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.COMMIT,
                hash = null,
            ),
        )

        assertThat(run.ingestedCount).isZero()
        assertThat(run.updatedCount).isZero()
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `ingest ignores unchanged file source id`() {
        val run = ingestionRun()
        val existing = artifact(hash = "same-hash")
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(artifactCommand(sourceId = existing.sourceId, hash = "same-hash"))

        assertThat(run.ingestedCount).isZero()
        assertThat(run.updatedCount).isZero()
        assertThat(existing.bodyText).isEqualTo("old content")
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `ingest updates changed file and increments updated count`() {
        val run = ingestionRun()
        val existing = artifact(hash = "old-hash")
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                bodyText = "new content",
                hash = "new-hash",
            ),
        )

        assertThat(run.ingestedCount).isZero()
        assertThat(run.updatedCount).isEqualTo(1)
        assertThat(existing.bodyText).isEqualTo("new content")
        assertThat(existing.hash).isEqualTo("new-hash")
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `addFailedArtifact appends failure and increments failed count`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)

        service.addFailedArtifact(
            ArtifactFailedCommand(
                transactionId = runId,
                repositoryOwner = "owner",
                repositoryName = "repo",
                sourceId = "source-id",
                sourceUrl = "https://github.com/owner/repo/blob/main/App.kt",
                artifactType = ArtifactType.FILE,
                reason = "Not found",
            ),
        )

        assertThat(run.failedCount).isEqualTo(1)
        val failedItem = run.failedItems.single()
        assertThat(failedItem.sourceId).isEqualTo("source-id")
        assertThat(failedItem.sourceUrl).isEqualTo("https://github.com/owner/repo/blob/main/App.kt")
        assertThat(failedItem.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(failedItem.reason).isEqualTo("Not found")
    }

    private fun artifactCommand(
        sourceId: String = "github:owner/repo:FILE:src/main/App.kt",
        artifactType: ArtifactType = ArtifactType.FILE,
        bodyText: String = "content",
        hash: String? = "hash-1",
    ) = ArtifactCommand(
        ingestionRunId = runId,
        sourceSystem = SourceSystem.GITHUB,
        sourceId = sourceId,
        sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
        artifactType = artifactType,
        title = "App.kt",
        bodyText = bodyText,
        mime = "text/x-kotlin",
        language = "Kotlin",
        createdAtSource = null,
        updatedAtSource = null,
        hash = hash,
    )

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.RUNNING,
    )

    private fun artifact(
        artifactType: ArtifactType = ArtifactType.FILE,
        hash: String?,
    ) = Artifact(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:${artifactType.name}:src/main/App.kt",
        sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
        artifactType = artifactType,
        title = "App.kt",
        bodyText = "old content",
        mime = "text/x-kotlin",
        language = "Kotlin",
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = ingestionRun(),
        hash = hash,
    )
}
