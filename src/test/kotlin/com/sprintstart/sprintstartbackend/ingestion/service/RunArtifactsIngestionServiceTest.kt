package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiDeindexResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiIngestResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.RunArtifactsIngestResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion.ArtifactAiMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.Optional
import java.util.UUID

class RunArtifactsIngestionServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val artifactIngestionClient = mockk<ArtifactIngestionClient>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val service = RunArtifactsIngestionService(
        ingestionRunRepository,
        artifactRepository,
        ArtifactAiMapper(),
        artifactIngestionClient,
        transactionManager,
    )

    private val runId = UUID.randomUUID()

    init {
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
    }

    @Test
    fun `ingestRunArtifacts sends artifacts an earlier run stored but this one changed`() = runTest {
        val touchedAgain = artifact()
        val run = ingestionRun().apply { artifactIdsToReingest.add(touchedAgain.id) }
        val request = slot<RunArtifactsAiSyncRequest>()

        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.of(run)
        every { artifactRepository.findAllByIngestionRunId(runId) } returns mutableListOf()
        every { artifactRepository.findAllById(listOf(touchedAgain.id)) } returns listOf(touchedAgain)
        coEvery { artifactIngestionClient.ingest(capture(request)) } returns succeeded(touchedAgain.id)

        service.ingestRunArtifacts(runId)

        // Without this the run looks empty: the artifact still belongs to the run that first
        // stored it, so findAllByIngestionRunId never returns it.
        assertThat(request.captured.artifactsToIngest.map { it.artifactId })
            .containsExactly(touchedAgain.id.toString())
    }

    @Test
    fun `ingestRunArtifacts sends an artifact only once when both queries return it`() = runTest {
        val artifact = artifact()
        val run = ingestionRun().apply { artifactIdsToReingest.add(artifact.id) }
        val request = slot<RunArtifactsAiSyncRequest>()

        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.of(run)
        every { artifactRepository.findAllByIngestionRunId(runId) } returns mutableListOf(artifact)
        every { artifactRepository.findAllById(emptyList()) } returns emptyList()
        coEvery { artifactIngestionClient.ingest(capture(request)) } returns succeeded(artifact.id)

        service.ingestRunArtifacts(runId)

        assertThat(request.captured.artifactsToIngest).hasSize(1)
    }

    @Test
    fun `ingestRunArtifacts skips a run with nothing to sync`() = runTest {
        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns
            Optional.of(ingestionRun())
        every { artifactRepository.findAllByIngestionRunId(runId) } returns mutableListOf()
        every { artifactRepository.findAllById(emptyList()) } returns emptyList()

        service.ingestRunArtifacts(runId)

        coVerify(exactly = 0) { artifactIngestionClient.ingest(any()) }
    }

    @Test
    fun `ingestRunArtifacts rejects a batch the AI service only partially indexed`() {
        val artifact = artifact()

        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns
            Optional.of(ingestionRun())
        every { artifactRepository.findAllByIngestionRunId(runId) } returns mutableListOf(artifact)
        every { artifactRepository.findAllById(emptyList()) } returns emptyList()
        coEvery { artifactIngestionClient.ingest(any()) } returns RunArtifactsIngestResponse(
            artifacts = listOf(
                ArtifactAiIngestResponse(
                    artifactId = artifact.id.toString(),
                    chunkCount = 0,
                    status = "failed",
                ),
            ),
        )

        // A 200 with a failed entry used to mark the run SUCCEEDED while its content was
        // missing from chat.
        assertThatThrownBy { runBlocking { service.ingestRunArtifacts(runId) } }
            .isInstanceOf(IngestionResponseException::class.java)
            .hasMessageContaining("1 artifact(s) failed to index")
    }

    @Test
    fun `ingestRunArtifacts rejects a batch whose deindex failed`() {
        val run = ingestionRun().apply { artifactIdsToDeindex.add(UUID.randomUUID().toString()) }

        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.of(run)
        every { artifactRepository.findAllByIngestionRunId(runId) } returns mutableListOf()
        every { artifactRepository.findAllById(emptyList()) } returns emptyList()
        coEvery { artifactIngestionClient.ingest(any()) } returns RunArtifactsIngestResponse(
            artifacts = emptyList(),
            deindexed = listOf(
                ArtifactAiDeindexResponse(
                    artifactId = run.artifactIdsToDeindex.first(),
                    status = "failed",
                    errorMessage = "collection locked",
                ),
            ),
        )

        // A failed deindex leaves deleted content answerable in chat.
        assertThatThrownBy { runBlocking { service.ingestRunArtifacts(runId) } }
            .isInstanceOf(IngestionResponseException::class.java)
            .hasMessageContaining("collection locked")
    }

    private fun succeeded(artifactId: UUID) = RunArtifactsIngestResponse(
        artifacts = listOf(
            ArtifactAiIngestResponse(artifactId = artifactId.toString(), chunkCount = 3),
        ),
    )

    private fun ingestionRun() = IngestionRun(
        id = runId,
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.COMPLETED,
    )

    private fun artifact() = Artifact(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:FILE:src/main/App.kt",
        sourceUrl = null,
        artifactType = ArtifactType.FILE,
        title = "App.kt",
        content = "content",
        mime = null,
        language = null,
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = ingestionRun(),
        hash = "hash",
    )
}
