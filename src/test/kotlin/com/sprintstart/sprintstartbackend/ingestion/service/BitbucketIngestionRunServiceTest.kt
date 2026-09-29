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

class BitbucketIngestionRunServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>()
    private val service = BitbucketIngestionRunService(ingestionRunRepository, ingestionRunLifeCycleService)

    private val runId = UUID.randomUUID()

    @Test
    fun `a run finishes only after files, commits and pull requests have all reported`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        justRun { ingestionRunLifeCycleService.finishRun(run) }

        service.markFetchPhaseFinished(runId, FinishedTypes.FILES)
        verify(exactly = 0) { ingestionRunLifeCycleService.finishRun(any<IngestionRun>()) }

        service.markFetchPhaseFinished(runId, FinishedTypes.COMMITS)
        verify(exactly = 0) { ingestionRunLifeCycleService.finishRun(any<IngestionRun>()) }

        // Bitbucket has no issue tracker and no org metadata, so the run must finish without those
        // phases; waiting for every FinishedTypes entry would leave it open forever.
        service.markFetchPhaseFinished(runId, FinishedTypes.PULL_REQUESTS)
        verify(exactly = 1) { ingestionRunLifeCycleService.finishRun(run) }
    }

    @Test
    fun `a phase reported twice does not finish the run twice`() {
        val run = ingestionRun()
        run.finishedTypes.addAll(PHASES - FinishedTypes.FILES)
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        justRun { ingestionRunLifeCycleService.finishRun(run) }

        service.markFetchPhaseFinished(runId, FinishedTypes.FILES)
        service.markFetchPhaseFinished(runId, FinishedTypes.FILES)

        verify(exactly = 1) { ingestionRunLifeCycleService.finishRun(run) }
    }

    @Test
    fun `a failed fetch phase is counted as a failure of the run`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        justRun { ingestionRunLifeCycleService.finishRun(run) }

        service.markFetchPhaseFailed(runId, FinishedTypes.COMMITS, "git fetch failed (exit 128)")

        // Closing the phase without counting the failure made a failed clone look exactly like a
        // repository that legitimately had nothing new.
        assertThat(run.failedCount).isEqualTo(1)
        assertThat(run.failedItems).singleElement().satisfies({ failure ->
            assertThat(failure.artifactType).isEqualTo(ArtifactType.COMMIT)
            assertThat(failure.reason).contains("git fetch failed (exit 128)")
        })
    }

    @Test
    fun `a failed phase still closes the phase so the run can finish`() {
        val run = ingestionRun()
        run.finishedTypes.addAll(PHASES - FinishedTypes.PULL_REQUESTS)
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)
        justRun { ingestionRunLifeCycleService.finishRun(run) }

        service.markFetchPhaseFailed(runId, FinishedTypes.PULL_REQUESTS, "bitbucket is down")

        assertThat(run.finishedTypes).containsAll(PHASES)
        verify(exactly = 1) { ingestionRunLifeCycleService.finishRun(run) }
    }

    @Test
    fun `a failed phase does not finish the run while other phases are still open`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.of(run)

        service.markFetchPhaseFailed(runId, FinishedTypes.FILES, "boom")

        verify(exactly = 0) { ingestionRunLifeCycleService.finishRun(any<IngestionRun>()) }
    }

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.BITBUCKET,
        status = IngestionRunStatus.RUNNING,
    )

    private companion object {
        val PHASES = setOf(
            FinishedTypes.FILES,
            FinishedTypes.COMMITS,
            FinishedTypes.PULL_REQUESTS,
        )
    }
}
