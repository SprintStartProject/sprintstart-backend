package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunAiSyncStatusService
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
    private val ingestionRunAiSyncStatusService = mockk<IngestionRunAiSyncStatusService>(relaxed = true)

    private val listener = IngestionEventListener(
        runArtifactsIngestionService,
        ingestionRunLifeCycleService,
        ingestionRunAiSyncStatusService,
        testScope,
    )

    @Test
    fun `handleRunFinished deindexes deleted artifacts and rolls up the run status`() {
        val runId = UUID.randomUUID()
        coEvery { runArtifactsIngestionService.deindexRunArtifacts(runId) } just runs

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { runArtifactsIngestionService.deindexRunArtifacts(runId) }
        // Indexing is no longer triggered here -- the drainer already handled it during the crawl,
        // so the run's status is derived from its artifacts rather than declared a success.
        verify(exactly = 1) { ingestionRunAiSyncStatusService.recompute(runId) }
        verify(exactly = 0) { ingestionRunLifeCycleService.markAiSyncSucceeded(any()) }
    }

    @Test
    fun `handleRunFinished records the failure instead of losing it silently`() {
        val runId = UUID.randomUUID()
        coEvery { runArtifactsIngestionService.deindexRunArtifacts(runId) } throws
            IllegalStateException("AI service unreachable")

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        verify(exactly = 1) {
            ingestionRunLifeCycleService.markAiSyncFailed(runId, "AI service unreachable")
        }
        // A failed deindex must not be overwritten by the roll-up in the same pass.
        verify(exactly = 0) { ingestionRunAiSyncStatusService.recompute(any()) }
    }
}
