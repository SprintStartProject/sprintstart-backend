package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class IngestionSourceStatusServiceTest {
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val service =
        IngestionSourceStatusService(githubRepositoryApi, ingestionRunRepository, artifactRepository)

    @Test
    fun `maps connected repository with its latest run counters and snapshot timestamps`() {
        val repositoryId = UUID.randomUUID()
        val commitsAt = Instant.parse("2026-07-06T10:00:00Z")
        val issuesAt = Instant.parse("2026-07-06T11:00:00Z")
        val prAt = Instant.parse("2026-07-06T12:00:00Z")
        val instance = GithubSourceInstanceDto(
            repositoryId = repositoryId,
            owner = "SprintStartProject",
            name = "sprintstart-frontend",
            status = "CONNECTED",
            enabled = true,
            lastCommitsSyncAt = commitsAt,
            lastIssuesSyncAt = issuesAt,
            lastPullRequestsSyncAt = prAt,
        )
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            sourceInstanceId = repositoryId,
            sourceInstanceRef = "SprintStartProject/sprintstart-frontend",
            startedAt = Instant.parse("2026-07-06T12:30:00Z"),
            ingestedCount = 42,
            updatedCount = 3,
            deletedCount = 1,
            failedCount = 0,
            status = IngestionRunStatus.COMPLETED,
            aiSyncStatus = AiSyncStatus.SUCCEEDED,
        )
        every { githubRepositoryApi.getSourceInstances(null) } returns listOf(instance)
        every { ingestionRunRepository.findFirstBySourceInstanceIdOrderByStartedAtDesc(repositoryId) } returns run
        every { artifactRepository.countByComponent("SprintStartProject/sprintstart-frontend") } returns 128

        val response = service.getStatusPerSourceInstance().single()

        assertThat(response.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(response.sourceId).isEqualTo("SprintStartProject/sprintstart-frontend")
        assertThat(response.repositoryId).isEqualTo(repositoryId)
        assertThat(response.owner).isEqualTo("SprintStartProject")
        assertThat(response.name).isEqualTo("sprintstart-frontend")
        assertThat(response.sourceUrl).isEqualTo("https://github.com/SprintStartProject/sprintstart-frontend")
        assertThat(response.connectionStatus).isEqualTo("CONNECTED")
        assertThat(response.enabled).isTrue()
        assertThat(response.lastRunTime).isEqualTo(run.startedAt)
        assertThat(response.ingestedCount).isEqualTo(42)
        assertThat(response.updatedCount).isEqualTo(3)
        assertThat(response.deletedCount).isEqualTo(1)
        assertThat(response.failedCount).isZero()
        assertThat(response.artifactCount).isEqualTo(128)
        assertThat(response.lastCommitsSyncAt).isEqualTo(commitsAt)
        assertThat(response.lastIssuesSyncAt).isEqualTo(issuesAt)
        assertThat(response.lastPullRequestsSyncAt).isEqualTo(prAt)
    }

    @Test
    fun `reports disabled status and empty counters when repository has no run or snapshot`() {
        val repositoryId = UUID.randomUUID()
        val instance = GithubSourceInstanceDto(
            repositoryId = repositoryId,
            owner = "owner",
            name = "repo",
            status = "DISABLED",
            enabled = false,
            lastCommitsSyncAt = null,
            lastIssuesSyncAt = null,
            lastPullRequestsSyncAt = null,
        )
        every { githubRepositoryApi.getSourceInstances(null) } returns listOf(instance)
        every { ingestionRunRepository.findFirstBySourceInstanceIdOrderByStartedAtDesc(repositoryId) } returns null
        every { artifactRepository.countByComponent("owner/repo") } returns 0

        val response = service.getStatusPerSourceInstance().single()

        assertThat(response.connectionStatus).isEqualTo("DISABLED")
        assertThat(response.enabled).isFalse()
        assertThat(response.lastRunTime).isNull()
        assertThat(response.ingestedCount).isZero()
        assertThat(response.artifactCount).isZero()
        assertThat(response.failedItems).isEmpty()
        assertThat(response.lastCommitsSyncAt).isNull()
        assertThat(response.lastIssuesSyncAt).isNull()
        assertThat(response.lastPullRequestsSyncAt).isNull()
    }

    @Test
    fun `filters by project id when provided`() {
        val projectId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        val instance = GithubSourceInstanceDto(
            repositoryId = repositoryId,
            owner = "owner",
            name = "repo",
            status = "OUT_OF_DATE",
            enabled = true,
            lastCommitsSyncAt = null,
            lastIssuesSyncAt = null,
            lastPullRequestsSyncAt = null,
        )
        every { githubRepositoryApi.getSourceInstances(projectId) } returns listOf(instance)
        every { ingestionRunRepository.findFirstBySourceInstanceIdOrderByStartedAtDesc(repositoryId) } returns null
        every { artifactRepository.countByComponent("owner/repo") } returns 5

        val response = service.getStatusPerSourceInstance(projectId).single()

        assertThat(response.repositoryId).isEqualTo(repositoryId)
        assertThat(response.connectionStatus).isEqualTo("OUT_OF_DATE")
        assertThat(response.artifactCount).isEqualTo(5)
    }
}
