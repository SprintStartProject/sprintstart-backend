package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactProjectsAiResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactProjectsAiSyncResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.UUID

class ArtifactProjectServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val artifactIngestionClient = mockk<ArtifactIngestionClient>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val service = ArtifactProjectService(
        artifactRepository,
        artifactIngestionClient,
        transactionManager,
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
        every { artifactRepository.findAllByComponent("acme/repo") } returns listOf(first, second)
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
        every { artifactRepository.findAllByComponent("acme/repo") } returns listOf(artifact)
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
        every { artifactRepository.findAllByComponent("acme/repo") } returns listOf(artifact)
        coEvery { artifactIngestionClient.syncProjectMemberships(any()) } returns succeeded(artifact.id)

        service.applyProjectLink(source, newProject, linked = true)

        assertThat(artifact.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
    }

    @Test
    fun `a source with no ingested artifacts completes without calling the AI service`() = runTest {
        every { artifactRepository.findAllByComponent("acme/repo") } returns emptyList()

        service.applyProjectLink(source, newProject, linked = true)

        coVerify(exactly = 0) { artifactIngestionClient.syncProjectMemberships(any()) }
    }

    @Test
    fun `a Jira instance resolves its artifacts by instance url`() = runTest {
        val artifact = artifact(existingProject)
        every {
            artifactRepository.findAllJiraArtifactsByInstanceUrl("https://acme.atlassian.net")
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
        every { artifactRepository.findAllByComponent("acme/repo") } returns listOf(artifact)
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
