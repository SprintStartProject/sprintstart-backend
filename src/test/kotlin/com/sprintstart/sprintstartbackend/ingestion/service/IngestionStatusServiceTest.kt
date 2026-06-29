package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class IngestionStatusServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val service = IngestionStatusService(ingestionRunRepository)

    @Test
    fun `getIngestionStatusPerSource returns empty github state when no runs exist`() {
        every { ingestionRunRepository.findFirstByOrderByStartedAt() } returns null

        val result = service.getIngestionStatusPerSource()

        val response = result.single()
        assertThat(response.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(response.lastRunTime).isNull()
        assertThat(response.ingestedCount).isZero()
        assertThat(response.updatedCount).isZero()
        assertThat(response.failedCount).isZero()
        assertThat(response.failedItems).isEmpty()
    }

    @Test
    fun `getIngestionStatusPerSource maps latest github run counters and failures`() {
        val failedItem = FailedArtifact(
            sourceId = "source-id",
            artifactType = ArtifactType.COMMIT,
            sourceUrl = "https://github.com/owner/repo/commit/abc",
            reason = "Git error",
        )
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            ingestedCount = 4,
            updatedCount = 1,
            failedCount = 1,
            failedItems = mutableListOf(failedItem),
            status = IngestionRunStatus.PARTIAL,
        )
        every { ingestionRunRepository.findFirstByOrderByStartedAt() } returns run

        val result = service.getIngestionStatusPerSource()

        val response = result.single()
        assertThat(response.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(response.lastRunTime).isEqualTo(run.startedAt)
        assertThat(response.ingestedCount).isEqualTo(4)
        assertThat(response.updatedCount).isEqualTo(1)
        assertThat(response.failedCount).isEqualTo(1)
        assertThat(response.failedItems).containsExactly(failedItem)
    }
}
