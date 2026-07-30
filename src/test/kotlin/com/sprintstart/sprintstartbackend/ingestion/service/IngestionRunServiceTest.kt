package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class IngestionRunServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()
    private val service = IngestionRunService(ingestionRunRepository, githubRepositoryApi)

    @Test
    fun `getRecentRuns returns empty list when repository has no runs`() {
        every { ingestionRunRepository.findByOrderByStartedAtDesc(any()) } returns emptyList()

        val result = service.getRecentRuns(limit = 5)

        assertThat(result).isEmpty()
        verify(exactly = 1) { ingestionRunRepository.findByOrderByStartedAtDesc(any()) }
    }

    @Test
    fun `getRecentRuns applies limit and maps counters and failed items`() {
        val pageable = slot<Pageable>()
        val failedItem = FailedArtifact(
            sourceId = "source-id",
            artifactType = ArtifactType.FILE,
            sourceUrl = "https://github.com/owner/repo/blob/main/App.kt",
            reason = "Not found",
        )
        val repositoryId = UUID.randomUUID()
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            sourceInstanceId = repositoryId,
            sourceInstanceRef = "SprintStartProject/sprintstart-frontend",
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            finishedAt = Instant.parse("2024-01-01T00:01:00Z"),
            ingestedCount = 3,
            updatedCount = 2,
            deletedCount = 1,
            failedCount = 1,
            failedItems = mutableListOf(failedItem),
            status = IngestionRunStatus.PARTIAL,
            failureReason = "Partial failure",
            aiSyncStatus = AiSyncStatus.FAILED,
            aiSyncFailureReason = "AI service unreachable",
        )
        every { ingestionRunRepository.findByOrderByStartedAtDesc(capture(pageable)) } returns listOf(run)

        val result = service.getRecentRuns(limit = 25)

        assertThat(pageable.captured.pageSize).isEqualTo(25)
        val response = result.single()
        assertThat(response.runId).isEqualTo(run.id)
        assertThat(response.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(response.sourceId).isEqualTo("SprintStartProject/sprintstart-frontend")
        assertThat(response.owner).isEqualTo("SprintStartProject")
        assertThat(response.name).isEqualTo("sprintstart-frontend")
        assertThat(response.repositoryId).isEqualTo(repositoryId)
        assertThat(response.startedAt).isEqualTo(run.startedAt)
        assertThat(response.finishedAt).isEqualTo(run.finishedAt)
        assertThat(response.ingestedCount).isEqualTo(3)
        assertThat(response.updatedCount).isEqualTo(2)
        assertThat(response.deletedCount).isEqualTo(1)
        assertThat(response.failedCount).isEqualTo(1)
        assertThat(response.failedItems).containsExactly(failedItem)
        assertThat(response.failureReason).isEqualTo("Partial failure")
        assertThat(response.aiSyncStatus).isEqualTo(AiSyncStatus.FAILED)
        assertThat(response.aiSyncFailureReason).isEqualTo("AI service unreachable")
    }

    @Test
    fun `getRecentRuns leaves sourceId null when repository metadata is absent`() {
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.UPLOAD,
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            status = IngestionRunStatus.COMPLETED,
            aiSyncStatus = AiSyncStatus.SUCCEEDED,
        )
        every { ingestionRunRepository.findByOrderByStartedAtDesc(any()) } returns listOf(run)

        val response = service.getRecentRuns(limit = 10).single()

        assertThat(response.sourceId).isNull()
        assertThat(response.owner).isNull()
        assertThat(response.name).isNull()
        assertThat(response.repositoryId).isNull()
    }

    @Test
    fun `getRun maps the run when it exists`() {
        val repositoryId = UUID.randomUUID()
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            sourceInstanceId = repositoryId,
            sourceInstanceRef = "SprintStartProject/sprintstart-frontend",
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            status = IngestionRunStatus.COMPLETED,
            aiSyncStatus = AiSyncStatus.SUCCEEDED,
        )
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        val response = service.getRun(run.id)

        assertThat(response.runId).isEqualTo(run.id)
        assertThat(response.sourceId).isEqualTo("SprintStartProject/sprintstart-frontend")
        assertThat(response.repositoryId).isEqualTo(repositoryId)
    }

    @Test
    fun `getRun returns not found for an unknown run`() {
        val runId = UUID.randomUUID()
        every { ingestionRunRepository.findById(runId) } returns Optional.empty()

        assertThatThrownBy { service.getRun(runId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getRuns returns a mapped page with metadata`() {
        val pageable = slot<Pageable>()
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            status = IngestionRunStatus.COMPLETED,
            aiSyncStatus = AiSyncStatus.SUCCEEDED,
        )
        every {
            ingestionRunRepository.findAll(any<Specification<IngestionRun>>(), capture(pageable))
        } returns PageImpl(listOf(run), PageRequest.of(0, 20), 1)

        val response = service.getRuns(page = 1, size = 20, status = IngestionRunStatus.COMPLETED)

        assertThat(pageable.captured.pageNumber).isEqualTo(0)
        assertThat(pageable.captured.pageSize).isEqualTo(20)
        assertThat(response.items).hasSize(1)
        assertThat(response.page.number).isEqualTo(1)
        assertThat(response.page.totalElements).isEqualTo(1)
        assertThat(response.page.hasNext).isFalse()
    }

    @Test
    fun `getRuns resolves projectId to connected repository ids`() {
        val projectId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { githubRepositoryApi.getRepositoryIdsByProject(projectId) } returns listOf(repositoryId)
        val emptyPage: Page<IngestionRun> = PageImpl(emptyList(), PageRequest.of(0, 20), 0)
        every { ingestionRunRepository.findAll(any<Specification<IngestionRun>>(), any<Pageable>()) } returns emptyPage

        val response = service.getRuns(page = 1, size = 20, projectId = projectId)

        verify(exactly = 1) { githubRepositoryApi.getRepositoryIdsByProject(projectId) }
        assertThat(response.items).isEmpty()
        assertThat(response.page.totalElements).isEqualTo(0)
    }
}
