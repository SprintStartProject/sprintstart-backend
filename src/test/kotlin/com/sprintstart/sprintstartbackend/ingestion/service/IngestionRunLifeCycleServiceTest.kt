package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

class IngestionRunLifeCycleServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = IngestionRunLifeCycleService(ingestionRunRepository, publisher)

    @Test
    fun `startOrUpdateRun marks a startup failure as not applicable for AI sync`() {
        val transactionId = UUID.randomUUID()
        every { ingestionRunRepository.findById(transactionId) } returns Optional.empty()
        val saved = slot<IngestionRun>()
        every { ingestionRunRepository.save(capture(saved)) } answers { firstArg() }

        service.startOrUpdateRun(transactionId, SourceSystem.GITHUB, IngestionRunStatus.FAILED, "boom")

        assertThat(saved.captured.aiSyncStatus).isEqualTo(AiSyncStatus.NOT_APPLICABLE)
    }

    @Test
    fun `startOrUpdateRun starts a new run as pending AI sync`() {
        val transactionId = UUID.randomUUID()
        every { ingestionRunRepository.findById(transactionId) } returns Optional.empty()
        val saved = slot<IngestionRun>()
        every { ingestionRunRepository.save(capture(saved)) } answers { firstArg() }

        service.startOrUpdateRun(transactionId, SourceSystem.GITHUB, IngestionRunStatus.RUNNING)

        assertThat(saved.captured.aiSyncStatus).isEqualTo(AiSyncStatus.PENDING)
    }

    @Test
    fun `markAiSyncSucceeded records success on the matching run`() {
        val run = run()
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        service.markAiSyncSucceeded(run.id)

        assertThat(run.aiSyncStatus).isEqualTo(AiSyncStatus.SUCCEEDED)
    }

    @Test
    fun `markAiSyncSucceeded is a no-op when the run is missing`() {
        val runId = UUID.randomUUID()
        every { ingestionRunRepository.findById(runId) } returns Optional.empty()

        service.markAiSyncSucceeded(runId)
    }

    @Test
    fun `markAiSyncFailed records the failure reason on the matching run`() {
        val run = run()
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        service.markAiSyncFailed(run.id, "AI service unreachable")

        assertThat(run.aiSyncStatus).isEqualTo(AiSyncStatus.FAILED)
        assertThat(run.aiSyncFailureReason).isEqualTo("AI service unreachable")
    }

    private fun run() = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.COMPLETED,
        aiSyncStatus = AiSyncStatus.PENDING,
    )
}
