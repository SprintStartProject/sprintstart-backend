package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiIngestResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.RunArtifactsIngestResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactAiSyncState
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion.ArtifactAiMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the incremental AI sync outbox: artifacts are drained in batches while a crawl is still
 * running, and each artifact's fate is decided by the AI service's own per-artifact
 * acknowledgement rather than by the HTTP status of the batch.
 */
class ArtifactAiSyncServiceTest {
    private val artifactRepository: ArtifactRepository = mockk()
    private val artifactAiMapper = ArtifactAiMapper()
    private val artifactIngestionClient: ArtifactIngestionClient = mockk()
    private val runStatusService: IngestionRunAiSyncStatusService = mockk(relaxed = true)
    private val transactionManager: PlatformTransactionManager = mockk()

    private val runId: UUID = UUID.randomUUID()

    private val service = ArtifactAiSyncService(
        artifactRepository,
        artifactAiMapper,
        artifactIngestionClient,
        runStatusService,
        transactionManager,
        batchSize = 2,
        maxAttempts = 2,
        retryBaseSeconds = 30,
    )

    init {
        // TransactionTemplate only needs a status object to hand back; the templates are exercised
        // for real so the read/HTTP/write split stays observable in these tests.
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { transactionManager.commit(any<TransactionStatus>()) } returns Unit
        every { transactionManager.rollback(any<TransactionStatus>()) } returns Unit
    }

    private val ingestionRun = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.RUNNING,
    )

    private fun artifact(
        labels: MutableList<String> = mutableListOf("good first issue"),
        state: ArtifactAiSyncState = ArtifactAiSyncState.PENDING,
        attempts: Int = 0,
    ) = Artifact(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:ISSUE:${UUID.randomUUID()}",
        sourceUrl = null,
        artifactType = ArtifactType.ISSUE,
        title = "Some issue",
        content = "body",
        mime = "text/markdown",
        language = null,
        labels = labels,
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = ingestionRun,
        hash = "hash",
        aiSyncState = state,
        aiSyncRunId = runId,
        aiSyncAttempts = attempts,
    )

    private fun stubPending(vararg artifacts: Artifact) {
        every { artifactRepository.findPendingAiSync(any(), any<Pageable>()) } returns artifacts.toList()
        every { artifactRepository.findAllById(any()) } returns artifacts.toMutableList()
    }

    @Test
    fun `marks acknowledged artifacts as synced`() = runTest {
        val pending = artifact()
        stubPending(pending)
        val captured = slot<RunArtifactsAiSyncRequest>()
        coEvery { artifactIngestionClient.ingest(capture(captured)) } returns RunArtifactsIngestResponse(
            artifacts = listOf(ArtifactAiIngestResponse(pending.id.toString(), chunkCount = 3, status = "completed")),
        )

        val drained = service.drainOnce()

        assertEquals(1, drained)
        assertEquals(ArtifactAiSyncState.SYNCED, pending.aiSyncState)
        assertNotNull(pending.aiSyncedAt)
        assertNull(pending.aiSyncError)
        // Deletions are the run-finished path's job; a drain only ever ingests.
        assertTrue(captured.captured.artifactsToDeindex.isEmpty())
        assertEquals(listOf("good first issue"), captured.captured.artifactsToIngest[0].labels)
        verify { runStatusService.recompute(runId) }
    }

    @Test
    fun `does not mark an artifact synced when the AI service reports it as failed`() = runTest {
        val pending = artifact()
        stubPending(pending)
        coEvery { artifactIngestionClient.ingest(any()) } returns RunArtifactsIngestResponse(
            artifacts = listOf(ArtifactAiIngestResponse(pending.id.toString(), chunkCount = 0, status = "failed")),
        )

        service.drainOnce()

        assertEquals(ArtifactAiSyncState.PENDING, pending.aiSyncState)
        assertEquals(1, pending.aiSyncAttempts)
        assertNotNull(pending.aiSyncError)
    }

    @Test
    fun `treats an unacknowledged artifact as a failed attempt`() = runTest {
        val acknowledged = artifact()
        val ignored = artifact()
        stubPending(acknowledged, ignored)
        coEvery { artifactIngestionClient.ingest(any()) } returns RunArtifactsIngestResponse(
            artifacts = listOf(
                ArtifactAiIngestResponse(acknowledged.id.toString(), chunkCount = 1, status = "completed"),
            ),
        )

        service.drainOnce()

        assertEquals(ArtifactAiSyncState.SYNCED, acknowledged.aiSyncState)
        assertEquals(ArtifactAiSyncState.PENDING, ignored.aiSyncState)
        assertEquals(1, ignored.aiSyncAttempts)
    }

    @Test
    fun `keeps artifacts owed and backs off when the whole request fails`() = runTest {
        val pending = artifact()
        stubPending(pending)
        coEvery { artifactIngestionClient.ingest(any()) } throws IllegalStateException("AI down")

        service.drainOnce()

        assertEquals(ArtifactAiSyncState.PENDING, pending.aiSyncState)
        assertEquals(1, pending.aiSyncAttempts)
        assertNotNull(pending.aiSyncNextAttemptAt)
        assertTrue(pending.aiSyncNextAttemptAt!!.isAfter(Instant.now()))
    }

    @Test
    fun `parks an artifact as failed once its attempt budget is exhausted`() = runTest {
        // maxAttempts = 2, so this artifact is on its last one.
        val pending = artifact(attempts = 1)
        stubPending(pending)
        coEvery { artifactIngestionClient.ingest(any()) } throws IllegalStateException("AI down")

        service.drainOnce()

        assertEquals(ArtifactAiSyncState.FAILED, pending.aiSyncState)
        assertNull(pending.aiSyncNextAttemptAt)
    }

    @Test
    fun `does not call the AI service when nothing is pending`() = runTest {
        every { artifactRepository.findPendingAiSync(any(), any<Pageable>()) } returns emptyList()

        assertEquals(0, service.drainOnce())

        coVerify(exactly = 0) { artifactIngestionClient.ingest(any()) }
    }
}
