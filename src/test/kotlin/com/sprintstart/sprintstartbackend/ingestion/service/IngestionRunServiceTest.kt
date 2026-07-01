package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

class IngestionRunServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val service = IngestionRunService(ingestionRunRepository)

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
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            finishedAt = Instant.parse("2024-01-01T00:01:00Z"),
            ingestedCount = 3,
            updatedCount = 2,
            failedCount = 1,
            failedItems = mutableListOf(failedItem),
            status = IngestionRunStatus.PARTIAL,
        )
        every { ingestionRunRepository.findByOrderByStartedAtDesc(capture(pageable)) } returns listOf(run)

        val result = service.getRecentRuns(limit = 25)

        assertThat(pageable.captured.pageSize).isEqualTo(25)
        val response = result.single()
        assertThat(response.runId).isEqualTo(run.id)
        assertThat(response.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(response.startedAt).isEqualTo(run.startedAt)
        assertThat(response.finishedAt).isEqualTo(run.finishedAt)
        assertThat(response.ingestedCount).isEqualTo(3)
        assertThat(response.updatedCount).isEqualTo(2)
        assertThat(response.failedCount).isEqualTo(1)
        assertThat(response.failedItems).containsExactly(failedItem)
    }
}
