package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

class IngestionStatusServiceCompletionTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = IngestionStatusService(ingestionRunRepository, publisher)

    @Test
    fun `markFetchPhaseFinished keeps run running until all phases complete`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.of(run)

        service.markFetchPhaseFinished(run.id, FinishedTypes.FILES)

        assertThat(run.finishedTypes).containsExactly(FinishedTypes.FILES)
        assertThat(run.status).isEqualTo(IngestionRunStatus.RUNNING)
        assertThat(run.finishedAt).isNotNull()
        verify(exactly = 0) { publisher.publishEvent(any()) }
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
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.of(run)

        service.markFetchPhaseFinished(run.id, FinishedTypes.PULL_REQUESTS)

        assertThat(run.status).isEqualTo(IngestionRunStatus.COMPLETED)
        assertThat(run.finishedTypes).containsAll(FinishedTypes.entries)
        assertThat(run.finishedAt).isNotNull()
        verify(exactly = 1) { publisher.publishEvent(RunFinishedEvent(run.id)) }
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
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.of(run)

        service.markFetchPhaseFinished(run.id, FinishedTypes.PULL_REQUESTS)

        assertThat(run.status).isEqualTo(IngestionRunStatus.PARTIAL)
        verify(exactly = 1) { publisher.publishEvent(RunFinishedEvent(run.id)) }
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
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.of(run)

        service.markFetchPhaseFinished(run.id, FinishedTypes.PULL_REQUESTS)

        assertThat(run.status).isEqualTo(IngestionRunStatus.FAILED)
        verify(exactly = 0) { publisher.publishEvent(any()) }
    }

    @Test
    fun `markFetchPhaseFinished throws when run is missing`() {
        val runId = UUID.randomUUID()
        every { ingestionRunRepository.findByIdForUpdate(runId) } returns Optional.empty()

        assertThatThrownBy { service.markFetchPhaseFinished(runId, FinishedTypes.FILES) }
            .isInstanceOf(IngestionRunNotFoundException::class.java)
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
