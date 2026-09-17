package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.AI_SYNC_STATUS_FAILED
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactProjectsAiResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactProjectsAiSyncResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ProjectMembershipsDeletedAiResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactProjectRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.UUID

class ArtifactProjectServiceTest {
    private val artifactProjectRepository = mockk<ArtifactProjectRepository>()
    private val artifactIngestionClient = mockk<ArtifactIngestionClient>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val artifactRepository = mockk<ArtifactRepository>()
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()

    private val service = ArtifactProjectService(
        artifactProjectRepository,
        artifactIngestionClient,
        transactionManager,
        artifactRepository,
        githubRepositoryApi,
    )

    private val source = ArtifactSourceRef.GithubRepository("acme", "repo")
    private val existingProject = UUID.randomUUID()
    private val newProject = UUID.randomUUID()

    init {
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
    }

    @Test
    fun `linking backfills the project onto every artifact of the source`() = runTest {
        val first = artifact(existingProject)
        val second = artifact(existingProject)
        val request = slot<ArtifactProjectsAiSyncRequest>()
        every { artifactProjectRepository.findAllByComponent("acme/repo") } returns listOf(first, second)
        coEvery { artifactIngestionClient.syncProjectMemberships(capture(request)) } returns
            succeeded(first.id, second.id)

        service.applyProjectLink(source, newProject, linked = true)

        assertThat(first.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
        assertThat(second.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
        // Without this the repository shows up in the new project with none of its content
        // findable there, because retrieval is fail-closed on the chunk markers.
        assertThat(request.captured.artifacts.map { it.artifactId })
            .containsExactlyInAnyOrder(first.id.toString(), second.id.toString())
        val sentMembership = request.captured.artifacts
            .first()
            .projectIds
        assertThat(sentMembership)
            .containsExactlyInAnyOrder(existingProject.toString(), newProject.toString())
    }

    @Test
    fun `unlinking drops only the one project`() = runTest {
        val artifact = artifact(existingProject, newProject)
        val request = slot<ArtifactProjectsAiSyncRequest>()
        every { artifactProjectRepository.findAllByComponent("acme/repo") } returns listOf(artifact)
        coEvery { artifactIngestionClient.syncProjectMemberships(capture(request)) } returns
            succeeded(artifact.id)

        service.applyProjectLink(source, newProject, linked = false)

        assertThat(artifact.projectIds).containsExactly(existingProject)
        val sentMembership = request.captured.artifacts
            .single()
            .projectIds
        assertThat(sentMembership).containsExactly(existingProject.toString())
    }

    @Test
    fun `linking is idempotent`() = runTest {
        val artifact = artifact(existingProject, newProject)
        every { artifactProjectRepository.findAllByComponent("acme/repo") } returns listOf(artifact)
        coEvery { artifactIngestionClient.syncProjectMemberships(any()) } returns succeeded(artifact.id)

        service.applyProjectLink(source, newProject, linked = true)

        assertThat(artifact.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
    }

    @Test
    fun `a source with no ingested artifacts completes without calling the AI service`() = runTest {
        every { artifactProjectRepository.findAllByComponent("acme/repo") } returns emptyList()

        service.applyProjectLink(source, newProject, linked = true)

        coVerify(exactly = 0) { artifactIngestionClient.syncProjectMemberships(any()) }
    }

    @Test
    fun `a Jira instance resolves its artifacts by instance url`() = runTest {
        val artifact = artifact(existingProject)
        every {
            artifactProjectRepository.findAllJiraArtifactsByInstanceUrl("https://acme.atlassian.net")
        } returns listOf(artifact)
        coEvery { artifactIngestionClient.syncProjectMemberships(any()) } returns succeeded(artifact.id)

        service.applyProjectLink(
            ArtifactSourceRef.JiraInstance("https://acme.atlassian.net"),
            newProject,
            linked = true,
        )

        assertThat(artifact.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
    }

    @Test
    fun `an artifact the AI service could not re-scope fails the operation`() {
        val artifact = artifact(existingProject)
        every { artifactProjectRepository.findAllByComponent("acme/repo") } returns listOf(artifact)
        coEvery { artifactIngestionClient.syncProjectMemberships(any()) } returns
            ArtifactProjectsAiSyncResponse(
                artifacts = listOf(
                    ArtifactProjectsAiResponse(
                        artifactId = artifact.id.toString(),
                        chunkCount = 0,
                        status = "failed",
                        errorMessage = "collection locked",
                    ),
                ),
            )

        assertThatThrownBy {
            runBlocking { service.applyProjectLink(source, newProject, linked = true) }
        }.isInstanceOf(IngestionResponseException::class.java)
            .hasMessageContaining("collection locked")
    }

    @Test
    fun `purging a deleted project clears it locally and in the index`() = runTest {
        val projectId = UUID.randomUUID()
        every { artifactProjectRepository.deleteProjectLinks(projectId) } returns 4
        coEvery { artifactIngestionClient.deleteProjectMemberships(projectId) } returns
            ProjectMembershipsDeletedAiResponse(
                projectId = projectId.toString(),
                chunkCount = 9,
                artifactCount = 4,
            )

        service.purgeProject(projectId)

        // Nothing else ever clears a deleted project's id from artifact_projects or from the
        // chunk markers.
        verify(exactly = 1) { artifactProjectRepository.deleteProjectLinks(projectId) }
        coVerify(exactly = 1) { artifactIngestionClient.deleteProjectMemberships(projectId) }
    }

    @Test
    fun `syncGithubOrgArtifact updates project ids and syncs AI on membership change`() = runTest {
        val orgArtifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "acme",
            sourceUrl = "https://github.com/acme",
            artifactType = ArtifactType.ORG_METADATA,
            title = "Acme",
            content = null,
            mime = null,
            language = null,
            projectIdsInternal = mutableSetOf(existingProject),
            createdAtSource = null,
            updatedAtSource = null,
            ingestionRun = IngestionRun(
                id = UUID.randomUUID(),
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.COMPLETED,
            ),
            hash = null,
        )
        val request = slot<ArtifactProjectsAiSyncRequest>()
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns orgArtifact
        every { githubRepositoryApi.getProjectIdsByOwner("acme") } returns setOf(existingProject, newProject)
        every { artifactRepository.save(orgArtifact) } returns orgArtifact
        coEvery { artifactIngestionClient.syncProjectMemberships(capture(request)) } returns
            succeeded(orgArtifact.id)

        service.syncGithubOrgArtifact("acme")

        assertThat(orgArtifact.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
        assertThat(request.captured.artifacts).hasSize(1)
        val firstArtifact = request.captured.artifacts.first()
        assertThat(firstArtifact.artifactId).isEqualTo(orgArtifact.id.toString())
        assertThat(firstArtifact.projectIds)
            .containsExactlyInAnyOrder(existingProject.toString(), newProject.toString())
        verify(exactly = 1) { artifactRepository.save(orgArtifact) }
    }

    @Test
    fun `syncGithubOrgArtifact is a no-op when membership is unchanged`() = runTest {
        val orgArtifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "acme",
            sourceUrl = "https://github.com/acme",
            artifactType = ArtifactType.ORG_METADATA,
            title = "Acme",
            content = null,
            mime = null,
            language = null,
            projectIdsInternal = mutableSetOf(existingProject),
            createdAtSource = null,
            updatedAtSource = null,
            ingestionRun = IngestionRun(
                id = UUID.randomUUID(),
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.COMPLETED,
            ),
            hash = null,
        )
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns orgArtifact
        every { githubRepositoryApi.getProjectIdsByOwner("acme") } returns setOf(existingProject)

        service.syncGithubOrgArtifact("acme")

        verify(exactly = 0) { artifactRepository.save(any()) }
        coVerify(exactly = 0) { artifactIngestionClient.syncProjectMemberships(any()) }
    }

    @Test
    fun `syncGithubOrgArtifact is a no-op when org artifact does not exist`() = runTest {
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns null

        service.syncGithubOrgArtifact("acme")

        verify(exactly = 0) { githubRepositoryApi.getProjectIdsByOwner(any()) }
        verify(exactly = 0) { artifactRepository.save(any()) }
        coVerify(exactly = 0) { artifactIngestionClient.syncProjectMemberships(any()) }
    }

    @Test
    fun `syncGithubOrgArtifact throws when AI sync fails`() {
        val orgArtifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "acme",
            sourceUrl = "https://github.com/acme",
            artifactType = ArtifactType.ORG_METADATA,
            title = "Acme",
            content = null,
            mime = null,
            language = null,
            projectIdsInternal = mutableSetOf(existingProject),
            createdAtSource = null,
            updatedAtSource = null,
            ingestionRun = IngestionRun(
                id = UUID.randomUUID(),
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.COMPLETED,
            ),
            hash = null,
        )
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns orgArtifact
        every { githubRepositoryApi.getProjectIdsByOwner("acme") } returns setOf(existingProject, newProject)
        every { artifactRepository.save(orgArtifact) } returns orgArtifact
        coEvery { artifactIngestionClient.syncProjectMemberships(any()) } returns ArtifactProjectsAiSyncResponse(
            artifacts = listOf(
                ArtifactProjectsAiResponse(
                    artifactId = orgArtifact.id.toString(),
                    chunkCount = 0,
                    status = AI_SYNC_STATUS_FAILED,
                    errorMessage = "ChromaDB connection timeout",
                ),
            ),
        )

        assertThatThrownBy {
            runBlocking { service.syncGithubOrgArtifact("acme") }
        }.isInstanceOf(IngestionResponseException::class.java)
            .hasMessageContaining("kept its old membership in the AI index")
    }

    private fun succeeded(vararg artifactIds: UUID) = ArtifactProjectsAiSyncResponse(
        artifacts = artifactIds.map {
            ArtifactProjectsAiResponse(artifactId = it.toString(), chunkCount = 2)
        },
    )

    private fun artifact(vararg projectIds: UUID) = Artifact(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:acme/repo:FILE:src/main/App.kt",
        sourceUrl = null,
        artifactType = ArtifactType.FILE,
        title = "App.kt",
        content = "content",
        mime = null,
        language = null,
        projectIdsInternal = projectIds.toMutableSet(),
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = "hash",
    )
}
