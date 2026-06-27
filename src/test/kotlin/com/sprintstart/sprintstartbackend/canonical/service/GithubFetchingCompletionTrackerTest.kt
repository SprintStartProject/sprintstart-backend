package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class GithubFetchingCompletionTrackerTest {
    private val artifactIngestionService = mockk<ArtifactIngestionService>()
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val tracker = GithubFetchingCompletionTracker(artifactIngestionService, ingestionRunRepository)

    @Test
    fun `markFetchPhaseFinished keeps run running until all phases complete`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        tracker.markFetchPhaseFinished(run.id, FinishedTypes.FILES)

        assertThat(run.finishedTypes).containsExactly(FinishedTypes.FILES)
        assertThat(run.status).isEqualTo(IngestionRunStatus.RUNNING)
        assertThat(run.finishedAt).isNotNull()
    }

    @Test
    fun `markFetchPhaseFinished marks completed when all phases complete without failures`() {
        val run = ingestionRun(
            finishedTypes = mutableSetOf(
                FinishedTypes.COMMITS,
                FinishedTypes.FILES,
                FinishedTypes.ISSUES,
            ),
        )
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        tracker.markFetchPhaseFinished(run.id, FinishedTypes.PULL_REQUESTS)

        assertThat(run.status).isEqualTo(IngestionRunStatus.COMPLETED)
        assertThat(run.finishedTypes).containsAll(FinishedTypes.entries)
        assertThat(run.finishedAt).isNotNull()
    }

    @Test
    fun `markFetchPhaseFinished marks partial when failures and successes exist`() {
        val run = ingestionRun(
            ingestedCount = 1,
            failedCount = 1,
            finishedTypes = mutableSetOf(
                FinishedTypes.COMMITS,
                FinishedTypes.FILES,
                FinishedTypes.ISSUES,
            ),
        )
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        tracker.markFetchPhaseFinished(run.id, FinishedTypes.PULL_REQUESTS)

        assertThat(run.status).isEqualTo(IngestionRunStatus.PARTIAL)
    }

    @Test
    fun `markFetchPhaseFinished marks failed when all phases complete with only failures`() {
        val run = ingestionRun(
            failedCount = 1,
            finishedTypes = mutableSetOf(
                FinishedTypes.COMMITS,
                FinishedTypes.FILES,
                FinishedTypes.ISSUES,
            ),
        )
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        tracker.markFetchPhaseFinished(run.id, FinishedTypes.PULL_REQUESTS)

        assertThat(run.status).isEqualTo(IngestionRunStatus.FAILED)
    }

    @Test
    fun `markFetchPhaseFinished throws when run is missing`() {
        val runId = UUID.randomUUID()
        every { ingestionRunRepository.findById(runId) } returns Optional.empty()

        assertThatThrownBy { tracker.markFetchPhaseFinished(runId, FinishedTypes.FILES) }
            .isInstanceOf(NoSuchElementException::class.java)
            .hasMessageContaining(runId.toString())
    }

    private fun ingestionRun(
        ingestedCount: Int = 0,
        failedCount: Int = 0,
        finishedTypes: MutableSet<FinishedTypes> = mutableSetOf(),
    ) = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.GITHUB,
        ingestedCount = ingestedCount,
        failedCount = failedCount,
        finishedTypes = finishedTypes,
        status = IngestionRunStatus.RUNNING,
    )
}
