package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.events.RunIndexedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.AiSyncStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactAiSyncState
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A run's `aiSyncStatus` is a roll-up of its artifacts' sync state now that syncing happens per
 * artifact rather than once per run.
 */
class IngestionRunAiSyncStatusServiceTest {
    private val ingestionRunRepository: IngestionRunRepository = mockk()
    private val artifactRepository: ArtifactRepository = mockk()
    private val publisher: ApplicationEventPublisher = mockk(relaxed = true)

    private val service =
        IngestionRunAiSyncStatusService(ingestionRunRepository, artifactRepository, publisher)

    private val runId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private fun run() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.COMPLETED,
        aiSyncStatus = AiSyncStatus.PENDING,
    )

    private fun stubCounts(pending: Long, failed: Long, synced: Long) {
        every {
            artifactRepository.countByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.PENDING)
        } returns pending
        every {
            artifactRepository.countByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.FAILED)
        } returns failed
        every {
            artifactRepository.countByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.SYNCED)
        } returns synced
        every {
            artifactRepository.findAllByAiSyncRunIdAndAiSyncState(runId, ArtifactAiSyncState.FAILED)
        } returns emptyList()
    }

    @Test
    fun `reports succeeded only once every artifact is indexed`() {
        val run = run()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 0, failed = 0, synced = 4)
        every { artifactRepository.findDistinctProjectIdsByAiSyncRunId(runId) } returns listOf(projectId)

        service.recompute(runId)

        assertEquals(AiSyncStatus.SUCCEEDED, run.aiSyncStatus)
        assertNull(run.aiSyncFailureReason)
    }

    /**
     * The corpus containing the run is the first moment anything can be derived from it, and this
     * is where that becomes observable outside ingestion.
     */
    @Test
    fun `announces the corpus is indexed, with the projects the run touched`() {
        val run = run()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 0, failed = 0, synced = 2)
        every { artifactRepository.findDistinctProjectIdsByAiSyncRunId(runId) } returns listOf(projectId)

        service.recompute(runId)

        verify(exactly = 1) { publisher.publishEvent(RunIndexedEvent(runId, setOf(projectId))) }
    }

    /**
     * The roll-up runs after every drained batch. Announcing each time would start a generation run
     * per batch instead of one per crawl.
     */
    @Test
    fun `announces once, not on every roll-up after the run is already indexed`() {
        val run = run().apply { aiSyncStatus = AiSyncStatus.SUCCEEDED }
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 0, failed = 0, synced = 2)

        service.recompute(runId)

        verify(exactly = 0) { publisher.publishEvent(any<RunIndexedEvent>()) }
    }

    @Test
    fun `says nothing while artifacts are still queued`() {
        val run = run()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 1, failed = 0, synced = 2)

        service.recompute(runId)

        verify(exactly = 0) { publisher.publishEvent(any<RunIndexedEvent>()) }
    }

    @Test
    fun `stays pending while anything is still owed, even alongside a failure`() {
        val run = run()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 1, failed = 1, synced = 2)

        service.recompute(runId)

        // A parked failure can still be picked up again by a later retry, so "owed" outranks
        // "gave up" -- reporting FAILED here would be premature.
        assertEquals(AiSyncStatus.PENDING, run.aiSyncStatus)
    }

    @Test
    fun `reports failed with a reason once nothing is left to retry`() {
        val run = run()
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 0, failed = 2, synced = 1)

        service.recompute(runId)

        assertEquals(AiSyncStatus.FAILED, run.aiSyncStatus)
        assertNotNull(run.aiSyncFailureReason)
    }

    @Test
    fun `leaves a run that touched no artifacts alone`() {
        val run = run().apply { aiSyncStatus = AiSyncStatus.NOT_APPLICABLE }
        every { ingestionRunRepository.findById(runId) } returns Optional.of(run)
        stubCounts(pending = 0, failed = 0, synced = 0)

        service.recompute(runId)

        assertEquals(AiSyncStatus.NOT_APPLICABLE, run.aiSyncStatus)
    }
}
