package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.RunArtifactsIngestResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The run-finished path no longer batches a run's artifacts: those are synced incrementally during
 * the crawl (see [ArtifactAiSyncServiceTest]). What remains here is deindexing, which cannot be
 * tracked per artifact because the rows are already deleted.
 */
class RunArtifactsIngestionServiceTest {
    private val ingestionRunRepository: IngestionRunRepository = mockk()
    private val artifactIngestionClient: ArtifactIngestionClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val service = RunArtifactsIngestionService(
        ingestionRunRepository,
        artifactIngestionClient,
        transactionManager,
    )

    private val runId = UUID.randomUUID()

    private fun ingestionRun(artifactIdsToDeindex: MutableList<String> = mutableListOf()) = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.COMPLETED,
        artifactIdsToDeindex = artifactIdsToDeindex,
    )

    @Test
    fun `dispatches only the deindex list for a finished run`() = runTest {
        val run = ingestionRun(artifactIdsToDeindex = mutableListOf("deleted-1", "deleted-2"))
        every { ingestionRunRepository.findWithArtifactIdsToDeindexById(runId) } returns Optional.of(run)
        val captured = slot<RunArtifactsAiSyncRequest>()
        coEvery { artifactIngestionClient.ingest(capture(captured)) } returns RunArtifactsIngestResponse(emptyList())

        service.deindexRunArtifacts(runId)

        assertEquals(listOf("deleted-1", "deleted-2"), captured.captured.artifactsToDeindex)
        assertTrue(captured.captured.artifactsToIngest.isEmpty())
    }

    @Test
    fun `skips dispatch when the run deleted nothing`() = runTest {
        every { ingestionRunRepository.findWithArtifactIdsToDeindexById(runId) } returns Optional.of(ingestionRun())

        service.deindexRunArtifacts(runId)

        coVerify(exactly = 0) { artifactIngestionClient.ingest(any()) }
    }

    @Test
    fun `throws when the run does not exist`() = runTest {
        every { ingestionRunRepository.findWithArtifactIdsToDeindexById(runId) } returns Optional.empty()

        assertThrows<IngestionRunNotFoundException> {
            service.deindexRunArtifacts(runId)
        }
    }
}
