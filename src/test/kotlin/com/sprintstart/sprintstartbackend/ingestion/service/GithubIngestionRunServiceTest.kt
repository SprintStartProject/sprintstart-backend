package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class GithubIngestionRunServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>()
    private val service = GithubIngestionRunService(ingestionRunRepository, ingestionRunLifeCycleService)

    private val runId = UUID.randomUUID()

    @Test
    fun `a failed fetch phase is counted as a failure of the run`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        justRun { ingestionRunLifeCycleService.finishRun(run) }

        service.markFetchPhaseFailed(runId, FinishedTypes.ISSUES, "Missing field 'updatedAt'")

        // Closing the phase without counting the failure made a rejected query look exactly like a
        // repository that legitimately has no issues.
        assertThat(run.failedCount).isEqualTo(1)
        assertThat(run.failedItems).singleElement().satisfies({ failure ->
            assertThat(failure.artifactType).isEqualTo(ArtifactType.ISSUE)
            assertThat(failure.reason).contains("Missing field 'updatedAt'")
        })
    }

    @Test
    fun `a failed phase still closes the phase so the run can finish`() {
        val run = ingestionRun()
        run.finishedTypes.addAll(FinishedTypes.entries - FinishedTypes.ISSUES)
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        justRun { ingestionRunLifeCycleService.finishRun(run) }

        service.markFetchPhaseFailed(runId, FinishedTypes.ISSUES, "boom")

        assertThat(run.finishedTypes).containsAll(FinishedTypes.entries)
        verify(exactly = 1) { ingestionRunLifeCycleService.finishRun(run) }
    }

    @Test
    fun `a failed phase does not finish the run while other phases are still open`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.markFetchPhaseFailed(runId, FinishedTypes.ISSUES, "boom")

        verify(exactly = 0) { ingestionRunLifeCycleService.finishRun(any<IngestionRun>()) }
    }

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.RUNNING,
    )
}
