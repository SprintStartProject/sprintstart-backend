package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import com.sprintstart.sprintstartbackend.ingestion.service.RunArtifactsIngestionService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IngestionEventListenerTest {
    private val testScope = TestScope()
    private val runArtifactsIngestionService = mockk<RunArtifactsIngestionService>()
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>(relaxed = true)

    private val listener = IngestionEventListener(
        runArtifactsIngestionService,
        ingestionRunLifeCycleService,
        testScope,
    )

    @Test
    fun `handleRunFinished marks the run as synced when the AI sync succeeds`() {
        val runId = UUID.randomUUID()
        coEvery { runArtifactsIngestionService.ingestRunArtifacts(runId) } just runs

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { runArtifactsIngestionService.ingestRunArtifacts(runId) }
        verify(exactly = 1) { ingestionRunLifeCycleService.markAiSyncSucceeded(runId) }
    }

    @Test
    fun `handleRunFinished records the failure instead of losing it silently`() {
        val runId = UUID.randomUUID()
        coEvery { runArtifactsIngestionService.ingestRunArtifacts(runId) } throws
            IllegalStateException("AI service unreachable")

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        verify(exactly = 1) {
            ingestionRunLifeCycleService.markAiSyncFailed(runId, "AI service unreachable")
        }
        verify(exactly = 0) { ingestionRunLifeCycleService.markAiSyncSucceeded(any()) }
    }
}
