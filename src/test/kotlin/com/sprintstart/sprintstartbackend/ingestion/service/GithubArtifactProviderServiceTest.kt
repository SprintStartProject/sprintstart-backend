package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
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
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("github:owner/repo:FILE:src/main/App.kt") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(artifactCommand())

        assertThat(savedArtifact.captured.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(savedArtifact.captured.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(savedArtifact.captured.metadata).isEqualTo("""{"repositoryFullName":"owner/repo"}""")
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
            artifactCommand(
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
            artifactCommand(
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

        service.persistArtifact(artifactCommand(sourceId = existing.sourceId, hash = "same-hash"))

        assertThat(existing.content).isEqualTo("old content")
        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 0) { artifactRepository.save(any()) }
        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
    }

    @Test
    fun `persistArtifact marks an unchanged file that gained a project for re-ingestion`() {
        val run = ingestionRun()
        val existing = artifact(hash = "same-hash")
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(artifactCommand(sourceId = existing.sourceId, hash = "same-hash"))

        assertThat(existing.content).isEqualTo("old content")
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        // Linking a repository to a second project changes nothing about what was fetched.
        assertThat(run.updatedCount).isZero()
        assertThat(existing.lastChangedAt).isNull()
    }

    @Test
    fun `persistArtifact updates changed file and increments updated count`() {
        val existing = artifact(hash = "old-hash")
        val run = existing.ingestionRun
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                bodyText = "new content",
                hash = "new-hash",
            ),
        )

        assertThat(existing.content).isEqualTo("new content")
        assertThat(existing.hash).isEqualTo("new-hash")
        assertThat(existing.lastChangedAt).isNotNull()
        assertThat(existing.projectIds).containsExactly(projectId)
        assertThat(run.updatedCount).isEqualTo(1)
        // Stored by an earlier run, so `findAllByIngestionRunId` cannot see it: without this the
        // updated content would never reach the AI index.
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `persistArtifact ignores a pull request whose title and body are unchanged`() {
        val existing = artifact(artifactType = ArtifactType.PULL_REQUEST, hash = null, projectIds = setOf(projectId))
        existing.title = "App.kt"
        existing.content = "content"
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.PULL_REQUEST,
                hash = null,
            ),
        )

        // Overwriting unconditionally used to count every re-fetch as an update and re-embed the
        // pull request for nothing.
        assertThat(existing.lastChangedAt).isNull()
        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
    }

    @Test
    fun `persistArtifact marks a re-fetched pull request for re-ingestion`() {
        val run = ingestionRun()
        val existing = artifact(artifactType = ArtifactType.PULL_REQUEST, hash = null, projectIds = setOf(projectId))
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.PULL_REQUEST,
                bodyText = "new body",
                hash = null,
            ),
        )

        assertThat(existing.content).isEqualTo("new body")
        assertThat(run.updatedCount).isEqualTo(1)
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        assertThat(existing.lastChangedAt).isNotNull()
    }

    @Test
    fun `persistArtifact saves new issue with state and labels`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("github:owner/repo:ISSUE:42") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(
            artifactCommand(
                sourceId = "github:owner/repo:ISSUE:42",
                artifactType = ArtifactType.ISSUE,
                state = "OPEN",
                labels = listOf("good first issue"),
            ),
        )

        assertThat(savedArtifact.captured.state).isEqualTo("OPEN")
        assertThat(savedArtifact.captured.labels).containsExactly("good first issue")
    }

    @Test
    fun `persistArtifact refreshes issue state and labels even when content is unchanged`() {
        val existing = artifact(
            artifactType = ArtifactType.ISSUE,
            hash = "same-hash",
            projectIds = setOf(projectId),
        ).apply {
            state = "OPEN"
            labels.add("bug")
        }
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(ingestionRun())

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.ISSUE,
                hash = "same-hash",
                state = "CLOSED",
                labels = listOf("bug", "good first issue"),
            ),
        )

        // Content itself is untouched (hash matched), but state/labels still refresh.
        assertThat(existing.content).isEqualTo("old content")
        assertThat(existing.state).isEqualTo("CLOSED")
        assertThat(existing.labels).containsExactly("bug", "good first issue")
        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    @Test
    fun `persistArtifact sends a closed issue back to the index`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.ISSUE,
            hash = "same-hash",
            projectIds = setOf(projectId),
        ).apply {
            state = "OPEN"
            labels.add("bug")
        }
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.ISSUE,
                hash = "same-hash",
                state = "CLOSED",
                labels = listOf("bug"),
            ),
        )

        // `state` is part of the AI payload, and starter-work mining offers only OPEN issues.
        // Refreshing it here and not re-indexing would keep the issue on offer forever.
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        // Still not a *content* change: no new text was fetched, so the run's update count and
        // `lastChangedAt` stay where they are.
        assertThat(run.updatedCount).isZero()
        assertThat(existing.lastChangedAt).isNull()
    }

    @Test
    fun `persistArtifact sends a re-labelled issue back to the index`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.ISSUE,
            hash = "same-hash",
            projectIds = setOf(projectId),
        ).apply {
            state = "OPEN"
            labels.add("bug")
        }
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.ISSUE,
                hash = "same-hash",
                state = "OPEN",
                labels = listOf("bug", "good first issue"),
            ),
        )

        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        assertThat(run.updatedCount).isZero()
    }

    @Test
    fun `persistArtifact leaves an issue nothing changed on alone`() {
        val existing = artifact(
            artifactType = ArtifactType.ISSUE,
            hash = "same-hash",
            projectIds = setOf(projectId),
        ).apply {
            state = "OPEN"
            labels.add("bug")
        }
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.ISSUE,
                hash = "same-hash",
                state = "OPEN",
                labels = listOf("bug"),
            ),
        )

        verify(exactly = 0) { ingestionRunRepository.findByIdForUpdate(any()) }
    }

    @Test
    fun `persistArtifact sends a re-opened pull request back to the index`() {
        val run = ingestionRun()
        val existing = artifact(
            artifactType = ArtifactType.PULL_REQUEST,
            hash = null,
            projectIds = setOf(projectId),
        ).apply { state = "CLOSED" }
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.PULL_REQUEST,
                bodyText = "old content",
                hash = null,
                state = "OPEN",
            ),
        )

        assertThat(existing.state).isEqualTo("OPEN")
        assertThat(run.artifactIdsToReingest).containsExactly(existing.id)
        assertThat(run.updatedCount).isZero()
    }

    @Test
    fun `persistArtifact stores the author login of a new issue`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("github:owner/repo:ISSUE:42") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(
            artifactCommand(
                sourceId = "github:owner/repo:ISSUE:42",
                artifactType = ArtifactType.ISSUE,
                authorLogin = "octocat",
            ),
        )

        assertThat(savedArtifact.captured.authorLogin).isEqualTo("octocat")
    }

    @Test
    fun `persistArtifact backfills a missing author login`() {
        val existing = artifact(artifactType = ArtifactType.ISSUE, hash = "same-hash", projectIds = setOf(projectId))
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(
            artifactCommand(
                sourceId = existing.sourceId,
                artifactType = ArtifactType.ISSUE,
                hash = "same-hash",
                authorLogin = "octocat",
            ),
        )

        // A row stored without an author picks one up on the next crawl, without the content
        // hash having to change.
        assertThat(existing.authorLogin).isEqualTo("octocat")
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

    @Test
    fun `persistArtifact saves new org metadata artifact without project ids`() {
        val run = ingestionRun()
        val savedArtifact = slot<Artifact>()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        every { artifactRepository.findBySourceId("octocat") } returns null
        every { artifactRepository.save(capture(savedArtifact)) } answers { savedArtifact.captured }

        service.persistArtifact(orgMetadataCommand())

        assertThat(savedArtifact.captured.artifactType).isEqualTo(ArtifactType.ORG_METADATA)
        assertThat(savedArtifact.captured.sourceId).isEqualTo("octocat")
        assertThat(savedArtifact.captured.projectIds).isEmpty()
        assertThat(run.ingestedCount).isEqualTo(1)
    }

    @Test
    fun `persistArtifact ignores duplicate org metadata source id`() {
        val existing = artifact(artifactType = ArtifactType.ORG_METADATA, hash = null)
        every { artifactRepository.findBySourceId(existing.sourceId) } returns existing

        service.persistArtifact(orgMetadataCommand(sourceId = existing.sourceId))

        verify(exactly = 0) { artifactRepository.save(any()) }
    }

    private fun orgMetadataCommand(
        sourceId: String = "octocat",
    ) = GithubArtifactCommand(
        ingestionRunId = runId,
        sourceSystem = SourceSystem.GITHUB,
        sourceId = sourceId,
        sourceUrl = "https://github.com/octocat",
        artifactType = ArtifactType.ORG_METADATA,
        title = "The Octocats",
        bodyText = null,
        mime = null,
        language = null,
        createdAtSource = null,
        updatedAtSource = null,
        hash = null,
        metadata = GithubOrgMetadataArtifactMetadata(
            login = "octocat",
            name = "The Octocats",
            description = null,
            company = null,
            blog = null,
            location = null,
            email = null,
            publicRepos = null,
            privateRepos = null,
            teams = null,
            members = emptyList(),
        ),
    )

    private fun artifactCommand(
        sourceId: String = "github:owner/repo:FILE:src/main/App.kt",
        artifactType: ArtifactType = ArtifactType.FILE,
        bodyText: String = "content",
        hash: String? = "hash-1",
        state: String? = null,
        labels: List<String> = emptyList(),
        authorLogin: String? = null,
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
        state = state,
        labels = labels,
        authorLogin = authorLogin,
    )

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.RUNNING,
    )

    private fun artifact(
        artifactType: ArtifactType = ArtifactType.FILE,
        hash: String?,
        projectIds: Set<UUID> = emptySet(),
    ) = Artifact(
        projectIdsInternal = projectIds.toMutableSet(),
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
