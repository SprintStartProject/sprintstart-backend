package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesResyncedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketWorkspaceMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.BitbucketArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
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
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BitbucketArtifactProviderServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val bitbucketRepositoryApi = mockk<BitbucketRepositoryApi>()
    private val artifactMetadataJsonMapper = mockk<ArtifactMetadataJsonMapper>()
    private val service = BitbucketArtifactProviderService(
        ingestionRunRepository,
        artifactRepository,
        bitbucketRepositoryApi,
        artifactMetadataJsonMapper,
    )

    private val runId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { artifactRepository.save(any()) } answers { firstArg() }
        every { bitbucketRepositoryApi.getRepositoryProjectIdsById(repositoryId) } returns setOf(projectId)
        every { bitbucketRepositoryApi.getWorkspaceProjectIds("sprintstart") } returns setOf(projectId)
        every { artifactMetadataJsonMapper.toJson(any()) } returns """{"repositoryId":"$repositoryId"}"""
    }

    @Test
    fun `persistArtifact saves new artifact and increments ingested count`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("bitbucket:sprintstart/backend:FILE:src/main/App.kt") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(fileCommand())

        assertThat(savedArtifact.captured.sourceSystem).isEqualTo(SourceSystem.BITBUCKET)
        assertThat(savedArtifact.captured.sourceId).isEqualTo("bitbucket:sprintstart/backend:FILE:src/main/App.kt")
        assertThat(savedArtifact.captured.metadata).isEqualTo("""{"repositoryId":"$repositoryId"}""")
        assertThat(savedArtifact.captured.projectIds).containsExactly(projectId)
        assertThat(savedArtifact.captured.title).isEqualTo("App.kt")
        assertThat(savedArtifact.captured.content).isEqualTo("content")
        assertThat(savedArtifact.captured.hash).isEqualTo("hash-1")
        assertThat(savedArtifact.captured.ingestionRun).isSameAs(run)
        assertThat(run.ingestedCount).isEqualTo(1)
    }

    @Test
    fun `persistArtifact ignores duplicate commit source id`() {
        val existing = artifact(artifactType = ArtifactType.COMMIT, hash = null, projectIds = setOf(projectId))
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            fileCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.COMMIT,
                hash = null,
            ),
        )

        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 0) { artifactRepository.save(any()) }
        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
    }

    @Test
    fun `persistArtifact marks a commit that gained a project for re-ingestion`() {
        val run = ingestionRun()
        val existing = artifact(artifactType = ArtifactType.COMMIT, hash = null)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(
            fileCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.COMMIT,
                hash = null,
            ),
        )

        assertThat(existing.projectIds).containsExactly(projectId)
        // The commit itself is unchanged, but it now belongs to a project whose chunks do not
        // carry that membership yet -- without the re-ingest it stays invisible there.
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        assertThat(run.updatedCount).isZero()
    }

    @Test
    fun `persistArtifact ignores unchanged file source id`() {
        val existing = artifact(hash = "same-hash", projectIds = setOf(projectId))
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(fileCommand(sourceId = existing.sourceId, hash = "same-hash"))

        assertThat(existing.content).isEqualTo("old content")
        verify(exactly = 0) { artifactRepository.save(any()) }
        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
    }

    @Test
    fun `persistArtifact updates a file whose hash changed`() {
        val run = ingestionRun()
        val existing = artifact(hash = "old-hash", projectIds = setOf(projectId))
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(fileCommand(sourceId = existing.sourceId, hash = "new-hash"))

        assertThat(existing.content).isEqualTo("content")
        assertThat(existing.hash).isEqualTo("new-hash")
        assertThat(run.updatedCount).isEqualTo(1)
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
    }

    @Test
    fun `persistArtifact merges a pull request without calling it a content change`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.PULL_REQUEST,
            state = "OPEN",
            projectIds = setOf(projectId),
        )
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        // Title and body match what the earlier run stored; only the state moved.
        service.persistArtifact(
            prCommand(
                sourceId = existing.sourceId,
                title = "old title",
                bodyText = "old content",
                state = "MERGED",
            ),
        )

        // Being merged moves none of the pull request's text, so it is tracking, not content: the
        // artifact still has to reach the index, but the run must not report an update.
        assertThat(existing.state).isEqualTo("MERGED")
        assertThat(run.updatedCount).isZero()
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
    }

    @Test
    fun `persistArtifact overwrites a pull request whose title changed`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.PULL_REQUEST,
            title = "old title",
            state = "OPEN",
            projectIds = setOf(projectId),
        )
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(prCommand(sourceId = existing.sourceId, title = "new title"))

        assertThat(existing.title).isEqualTo("new title")
        assertThat(run.updatedCount).isEqualTo(1)
    }

    @Test
    fun `persistArtifact stores workspace metadata with the workspace's project union`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("bitbucket:sprintstart:ORG_METADATA") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(workspaceCommand())

        // A workspace is no repository, so the union of its connections' projects decides — never
        // a per-repository lookup.
        verify(exactly = 0) { bitbucketRepositoryApi.getRepositoryProjectIdsById(any()) }
        verify { bitbucketRepositoryApi.getWorkspaceProjectIds("sprintstart") }
        assertThat(savedArtifact.captured.sourceId).isEqualTo("bitbucket:sprintstart:ORG_METADATA")
        assertThat(savedArtifact.captured.artifactType).isEqualTo(ArtifactType.ORG_METADATA)
        assertThat(savedArtifact.captured.title).isEqualTo("SprintStart")
        assertThat(savedArtifact.captured.content).isNull()
        assertThat(savedArtifact.captured.projectIds).containsExactly(projectId)
        assertThat(run.ingestedCount).isEqualTo(1)
    }

    @Test
    fun `persistArtifact ignores a re-delivered workspace artifact whose payload is unchanged`() {
        val existing = artifact(
            artifactType = ArtifactType.ORG_METADATA,
            title = "SprintStart",
            projectIds = setOf(projectId),
        )
        every { artifactRepository.findBySourceId("bitbucket:sprintstart:ORG_METADATA") } returns existing
        every { artifactMetadataJsonMapper.toJson(any()) } returns "{}"
        existing.metadata = "{}"

        service.persistArtifact(workspaceCommand())

        // Same members, same projects: the run is not even locked a second time.
        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `persistArtifact refreshes a workspace artifact whose payload changed`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.ORG_METADATA,
            title = "SprintStart",
            projectIds = setOf(projectId),
        )
        existing.metadata = "{}"
        every { artifactRepository.findBySourceId("bitbucket:sprintstart:ORG_METADATA") } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(workspaceCommand(title = "SprintStart Renamed"))

        assertThat(existing.metadata).isEqualTo("""{"repositoryId":"$repositoryId"}""")
        assertThat(existing.title).isEqualTo("SprintStart Renamed")
        assertThat(run.updatedCount).isEqualTo(1)
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
    }

    @Test
    fun `persistArtifact links a re-delivered workspace artifact to newly connected projects`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.ORG_METADATA,
            title = "SprintStart",
            projectIds = emptySet(),
        )
        existing.metadata = "{}"
        every { artifactRepository.findBySourceId("bitbucket:sprintstart:ORG_METADATA") } returns existing
        every { artifactMetadataJsonMapper.toJson(any()) } returns "{}"
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(workspaceCommand())

        // Payload identical, so this is linking, not content: re-indexed, but no update counted.
        assertThat(existing.projectIds).containsExactly(projectId)
        assertThat(run.updatedCount).isZero()
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
    }

    @Test
    fun `persistArtifact rejects artifact types Bitbucket does not produce`() {
        val existing = artifact(artifactType = ArtifactType.PAGE, hash = null)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        assertThatThrownBy {
            service.persistArtifact(
                fileCommand(sourceId = existing.sourceId, artifactType = ArtifactType.PAGE),
            )
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `persistArtifact fails when the run is unknown`() {
        every { artifactRepository.findBySourceId(any()) } returns null
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.empty()

        assertThatThrownBy { service.persistArtifact(fileCommand()) }
            .isInstanceOf(IngestionRunNotFoundException::class.java)
    }

    @Test
    fun `reconcileDeletedFiles removes stored files the full ingest did not see`() {
        val run = ingestionRun()
        val kept = artifact(sourceId = "bitbucket:sprintstart/backend:FILE:kept.kt")
        val stale = artifact(sourceId = "bitbucket:sprintstart/backend:FILE:gone.kt")
        val commit = artifact(
            artifactType = ArtifactType.COMMIT,
            sourceId = "bitbucket:sprintstart/backend:COMMIT:abc123",
        )
        every {
            artifactRepository.findAllBySourceIdPrefix("bitbucket:sprintstart/backend:FILE:")
        } returns listOf(kept, stale, commit)
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.deleteById(any()) } returns Unit

        service.reconcileDeletedFiles(
            BitbucketFilesResyncedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                workspace = "sprintstart",
                slug = "backend",
                visitedPaths = setOf("kept.kt"),
            ),
        )

        verify { artifactRepository.deleteById(stale.id) }
        verify(exactly = 0) { artifactRepository.deleteById(kept.id) }
        verify(exactly = 0) { artifactRepository.deleteById(commit.id) }
        assertThat(run.deletedCount).isEqualTo(1)
        assertThat(run.artifactIdsToDeindex).containsExactly(stale.id.toString())
    }

    @Test
    fun `reconcileDeletedFiles leaves the run alone when nothing is stale`() {
        val kept = artifact(sourceId = "bitbucket:sprintstart/backend:FILE:kept.kt")
        every {
            artifactRepository.findAllBySourceIdPrefix("bitbucket:sprintstart/backend:FILE:")
        } returns listOf(kept)

        service.reconcileDeletedFiles(
            BitbucketFilesResyncedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                workspace = "sprintstart",
                slug = "backend",
                visitedPaths = setOf("kept.kt"),
            ),
        )

        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { artifactRepository.deleteById(any()) }
    }

    @Test
    fun `deleteFileArtifact removes the artifact and records it for deindexing`() {
        val run = ingestionRun()
        val existing = artifact(hash = "hash", projectIds = setOf(projectId))
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every {
            artifactRepository.findBySourceId("bitbucket:sprintstart/backend:FILE:src/main/App.kt")
        } returns existing
        every { artifactRepository.deleteById(existing.id) } returns Unit

        service.deleteFileArtifact(
            BitbucketFileDeletedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                workspace = "sprintstart",
                slug = "backend",
                path = "src/main/App.kt",
            ),
        )

        assertThat(run.deletedCount).isEqualTo(1)
        assertThat(run.artifactIdsToDeindex).containsExactly(existing.id.toString())
    }

    @Test
    fun `deleteFileArtifact leaves the run unchanged when the file was never stored`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId(any()) } returns null

        service.deleteFileArtifact(
            BitbucketFileDeletedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                workspace = "sprintstart",
                slug = "backend",
                path = "unknown.txt",
            ),
        )

        assertThat(run.deletedCount).isZero()
        assertThat(run.artifactIdsToDeindex).isEmpty()
    }

    private fun fileCommand(
        sourceId: String = "bitbucket:sprintstart/backend:FILE:src/main/App.kt",
        artifactType: ArtifactType = ArtifactType.FILE,
        hash: String? = "hash-1",
    ) = BitbucketArtifactCommand(
        ingestionRunId = runId,
        sourceSystem = SourceSystem.BITBUCKET,
        sourceId = sourceId,
        sourceUrl = "https://bitbucket.org/sprintstart/backend/src/rev/src/main/App.kt",
        artifactType = artifactType,
        title = "App.kt",
        bodyText = "content",
        mime = "text/x-kotlin",
        language = "Kotlin",
        createdAtSource = null,
        updatedAtSource = null,
        hash = hash,
        metadata = BitbucketArtifactMetadata(
            repositoryId = repositoryId,
            workspace = "sprintstart",
            slug = "backend",
        ),
    )

    private fun workspaceCommand(
        sourceId: String = "bitbucket:sprintstart:ORG_METADATA",
        title: String = "SprintStart",
    ) = BitbucketArtifactCommand(
        ingestionRunId = runId,
        sourceSystem = SourceSystem.BITBUCKET,
        sourceId = sourceId,
        sourceUrl = "https://bitbucket.org/sprintstart",
        artifactType = ArtifactType.ORG_METADATA,
        title = title,
        bodyText = null,
        mime = null,
        language = null,
        createdAtSource = null,
        updatedAtSource = null,
        hash = null,
        metadata = BitbucketWorkspaceMetadataArtifactMetadata(
            workspace = "sprintstart",
            uuid = "{ws-uuid}",
            name = "SprintStart",
            isPrivate = true,
            createdOn = "2024-01-01T00:00:00Z",
            url = "https://bitbucket.org/sprintstart",
            members = emptyList(),
        ),
    )

    private fun prCommand(
        sourceId: String = "bitbucket:sprintstart/backend:PULL_REQUEST:7",
        title: String = "PR #7 Improve docs",
        bodyText: String = "body",
        state: String = "OPEN",
    ) = BitbucketArtifactCommand(
        ingestionRunId = runId,
        sourceSystem = SourceSystem.BITBUCKET,
        sourceId = sourceId,
        sourceUrl = "https://bitbucket.org/sprintstart/backend/pull-requests/7",
        artifactType = ArtifactType.PULL_REQUEST,
        title = title,
        bodyText = bodyText,
        mime = null,
        language = null,
        createdAtSource = Instant.parse("2026-03-02T00:00:00Z"),
        updatedAtSource = null,
        hash = null,
        metadata = BitbucketArtifactMetadata(
            repositoryId = repositoryId,
            workspace = "sprintstart",
            slug = "backend",
        ),
        state = state,
        mergedAtSource = null,
        firstResponseAtSource = null,
        changesRequestedCount = 0,
    )

    private fun artifact(
        artifactType: ArtifactType = ArtifactType.FILE,
        hash: String? = "old-hash",
        title: String = "old title",
        state: String? = null,
        projectIds: Set<UUID> = emptySet(),
        sourceId: String = "bitbucket:sprintstart/backend:FILE:src/main/App.kt",
    ) = Artifact(
        sourceSystem = SourceSystem.BITBUCKET,
        sourceId = sourceId,
        sourceUrl = null,
        artifactType = artifactType,
        title = title,
        content = "old content",
        mime = null,
        language = null,
        state = state,
        projectIdsInternal = projectIds.toMutableSet(),
        metadata = "{}",
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = ingestionRun(),
        hash = hash,
    )

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.BITBUCKET,
        status = IngestionRunStatus.RUNNING,
    )
}
