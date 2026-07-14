package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
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

class GithubArtifactProviderServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()
    private val artifactMetadataJsonMapper = mockk<ArtifactMetadataJsonMapper>()
    private val service = GithubArtifactProviderService(
        ingestionRunRepository,
        artifactRepository,
        githubRepositoryApi,
        artifactMetadataJsonMapper,
    )

    private val runId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { artifactRepository.save(any()) } answers { firstArg() }
        every { githubRepositoryApi.getRepositoryProjectIdsById(repositoryId) } returns setOf(projectId)
        every { artifactMetadataJsonMapper.toJson(any()) } returns """{"repositoryFullName":"owner/repo"}"""
    }

    @Test
    fun `persistArtifact saves new artifact and increments ingested count`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.getReferenceById(runId) } returns run
        every { artifactRepository.findBySourceId("github:owner/repo:FILE:src/main/App.kt") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }
        every { ingestionRunRepository.incrementIngestedCount(runId) } returns Unit

        service.persistArtifact(artifactCommand())

        assertThat(savedArtifact.captured.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(savedArtifact.captured.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(savedArtifact.captured.metadata).isEqualTo("""{"repositoryFullName":"owner/repo"}""")
        assertThat(savedArtifact.captured.projectIds).containsExactly(projectId)
        assertThat(savedArtifact.captured.title).isEqualTo("App.kt")
        assertThat(savedArtifact.captured.content).isEqualTo("content")
        assertThat(savedArtifact.captured.hash).isEqualTo("hash-1")
        assertThat(savedArtifact.captured.ingestionRun).isSameAs(run)
        verify(exactly = 1) { ingestionRunRepository.incrementIngestedCount(runId) }
    }

    @Test
    fun `persistArtifact ignores duplicate commit source id`() {
        val existing = artifact(artifactType = ArtifactType.COMMIT, hash = null)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.COMMIT,
                hash = null,
            ),
        )

        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `persistArtifact ignores unchanged file source id`() {
        val existing = artifact(hash = "same-hash")
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(artifactCommand(sourceId = existing.sourceId, hash = "same-hash"))

        assertThat(existing.content).isEqualTo("old content")
        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `persistArtifact updates changed file and increments updated count`() {
        val existing = artifact(hash = "old-hash")
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.incrementUpdatedCount(runId) } returns Unit

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                bodyText = "new content",
                hash = "new-hash",
            ),
        )

        assertThat(existing.content).isEqualTo("new content")
        assertThat(existing.hash).isEqualTo("new-hash")
        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 1) { ingestionRunRepository.incrementUpdatedCount(runId) }
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `deleteFileArtifact deletes existing artifact and records deindex id`() {
        val run = ingestionRun()
        val existing = artifact(hash = "hash")
        val event = GithubFileDeletedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
        )
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { artifactRepository.deleteById(existing.id) } returns Unit

        service.deleteFileArtifact(event)

        assertThat(run.deletedCount).isEqualTo(1)
        assertThat(run.artifactIdsToDeindex).containsExactly(existing.id.toString())
        verify(exactly = 1) { artifactRepository.deleteById(existing.id) }
    }

    @Test
    fun `deleteFileArtifact throws when run is missing`() {
        val event = GithubFileDeletedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
        )
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.empty()

        assertThatThrownBy { service.deleteFileArtifact(event) }
            .isInstanceOf(IngestionRunNotFoundException::class.java)
            .hasMessageContaining(runId.toString())
    }

    private fun artifactCommand(
        sourceId: String = "github:owner/repo:FILE:src/main/App.kt",
        artifactType: ArtifactType = ArtifactType.FILE,
        bodyText: String = "content",
        hash: String? = "hash-1",
    ) = GithubArtifactCommand(
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
        metadata = GithubArtifactMetadata(
            repositoryId = repositoryId,
            repositoryFullName = "owner/repo",
        ),
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
        content = "old content",
        mime = "text/x-kotlin",
        language = "Kotlin",
        metadata = """{"repositoryFullName":"owner/repo"}""",
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = ingestionRun(),
        hash = hash,
    )
}
