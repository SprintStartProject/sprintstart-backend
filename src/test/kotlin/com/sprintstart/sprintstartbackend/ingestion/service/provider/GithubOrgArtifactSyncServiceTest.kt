package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactProjectsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.AI_SYNC_STATUS_FAILED
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
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.UUID

class GithubOrgArtifactSyncServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()
    private val artifactIngestionClient = mockk<ArtifactIngestionClient>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val service = GithubOrgArtifactSyncService(
        artifactRepository,
        githubRepositoryApi,
        artifactIngestionClient,
        transactionManager,
    )

    private val existingProject = UUID.randomUUID()
    private val newProject = UUID.randomUUID()

    init {
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
    }

    @Test
    fun `syncOrgArtifact updates project ids and syncs AI on membership change`() = runTest {
        val orgArtifact = orgArtifact(existingProject)
        val request = slot<ArtifactProjectsAiSyncRequest>()
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns orgArtifact
        every { githubRepositoryApi.getProjectIdsByOwner("acme") } returns setOf(existingProject, newProject)
        every { artifactRepository.save(orgArtifact) } returns orgArtifact
        coEvery { artifactIngestionClient.syncProjectMemberships(capture(request)) } returns
            succeeded(orgArtifact.id)

        service.syncOrgArtifact("acme")

        assertThat(orgArtifact.projectIds).containsExactlyInAnyOrder(existingProject, newProject)
        assertThat(request.captured.artifacts).hasSize(1)
        val firstArtifact = request.captured.artifacts.first()
        assertThat(firstArtifact.artifactId).isEqualTo(orgArtifact.id.toString())
        assertThat(firstArtifact.projectIds)
            .containsExactlyInAnyOrder(existingProject.toString(), newProject.toString())
        verify(exactly = 1) { artifactRepository.save(orgArtifact) }
    }

    @Test
    fun `syncOrgArtifact is a no-op when membership is unchanged`() = runTest {
        val orgArtifact = orgArtifact(existingProject)
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns orgArtifact
        every { githubRepositoryApi.getProjectIdsByOwner("acme") } returns setOf(existingProject)

        service.syncOrgArtifact("acme")

        verify(exactly = 0) { artifactRepository.save(any()) }
        coVerify(exactly = 0) { artifactIngestionClient.syncProjectMemberships(any()) }
    }

    @Test
    fun `syncOrgArtifact is a no-op when org artifact does not exist`() = runTest {
        every { artifactRepository.findOrgMetadataArtifact(SourceSystem.GITHUB, "acme") } returns null

        service.syncOrgArtifact("acme")

        verify(exactly = 0) { githubRepositoryApi.getProjectIdsByOwner(any()) }
        verify(exactly = 0) { artifactRepository.save(any()) }
        coVerify(exactly = 0) { artifactIngestionClient.syncProjectMemberships(any()) }
    }

    @Test
    fun `syncOrgArtifact throws when AI sync fails`() {
        val orgArtifact = orgArtifact(existingProject)
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
            runBlocking { service.syncOrgArtifact("acme") }
        }.isInstanceOf(IngestionResponseException::class.java)
            .hasMessageContaining("kept its old membership in the AI index")
    }

    private fun orgArtifact(vararg projectIds: UUID) = Artifact(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "acme",
        sourceUrl = "https://github.com/acme",
        artifactType = ArtifactType.ORG_METADATA,
        title = "Acme",
        content = null,
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
        hash = null,
    )

    private fun succeeded(vararg artifactIds: UUID) = ArtifactProjectsAiSyncResponse(
        artifacts = artifactIds.map {
            ArtifactProjectsAiResponse(artifactId = it.toString(), chunkCount = 2)
        },
    )
}
