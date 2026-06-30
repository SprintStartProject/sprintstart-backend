package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionStatusService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubFileListenerTest {
    private val artifactIngestionService = mockk<ArtifactIngestionService>()
    private val artifactMapper = mockk<GithubArtifactMapper>()
    private val failedMapper = mockk<GithubArtifactFailedMapper>()
    private val ingestionStatusService = mockk<IngestionStatusService>()
    private val listener = GithubFileListener(
        artifactIngestionService,
        artifactMapper,
        failedMapper,
        ingestionStatusService,
    )

    @Test
    fun `file fetched event maps and ingests artifact`() {
        val event = fileFetchedEvent()
        val command = artifactCommand(event.transactionId)
        every { artifactMapper.toCommand(event) } returns command
        every { artifactIngestionService.ingest(command) } just runs

        listener.on(event)

        verify(exactly = 1) { artifactIngestionService.ingest(command) }
    }

    @Test
    fun `file fetch failed event maps and records failed artifact`() {
        val event = GithubFileFetchFailedEvent(
            transactionId = UUID.randomUUID(),
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
            reason = "Missing",
        )
        val command = ArtifactFailedCommand(
            transactionId = event.transactionId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            sourceId = "github:owner/repo:FILE:src/main/App.kt",
            sourceUrl = null,
            artifactType = ArtifactType.FILE,
            reason = "Missing",
        )
        every { failedMapper.toCommand(event) } returns command
        every { artifactIngestionService.addFailedArtifact(command) } just runs

        listener.on(event)

        verify(exactly = 1) { artifactIngestionService.addFailedArtifact(command) }
    }

    @Test
    fun `files completed event marks files phase finished`() {
        val runId = UUID.randomUUID()
        every { ingestionStatusService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(
            GithubFilesFetchCompletedEvent(
                transactionId = runId,
                repositoryOwner = "owner",
                repositoryName = "repo",
            ),
        )

        verify(exactly = 1) {
            ingestionStatusService.markFetchPhaseFinished(runId, FinishedTypes.FILES)
        }
    }

    @Test
    fun `files failed event marks files phase finished`() {
        val runId = UUID.randomUUID()
        every { ingestionStatusService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(
            GithubFilesFetchFailedEvent(
                transactionId = runId,
                repositoryOwner = "owner",
                repositoryName = "repo",
                reason = "Git failed",
            ),
        )

        verify(exactly = 1) {
            ingestionStatusService.markFetchPhaseFinished(runId, FinishedTypes.FILES)
        }
    }

    @Test
    fun `file deleted event un-ingests file artifact`() {
        val event = GithubFileDeletedEvent(
            transactionId = UUID.randomUUID(),
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
        )
        every { artifactIngestionService.unIngestFileArtifact(event) } just runs

        listener.on(event)

        verify(exactly = 1) { artifactIngestionService.unIngestFileArtifact(event) }
    }

    private fun fileFetchedEvent() = GithubFileFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryOwner = "owner",
        repositoryName = "repo",
        path = "src/main/App.kt",
        content = "content",
        sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
    )

    private fun artifactCommand(runId: UUID) = ArtifactCommand(
        ingestionRunId = runId,
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:FILE:src/main/App.kt",
        sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
        artifactType = ArtifactType.FILE,
        title = "App.kt",
        bodyText = "content",
        mime = "text/x-kotlin",
        language = "Kotlin",
        createdAtSource = null,
        updatedAtSource = null,
        hash = "hash",
    )
}
