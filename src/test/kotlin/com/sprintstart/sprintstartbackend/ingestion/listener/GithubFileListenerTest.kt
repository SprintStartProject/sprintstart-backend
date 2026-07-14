package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubFileListener
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.FailedArtifactService
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubFileListenerTest {
    private val repositoryId = UUID.randomUUID()
    private val githubArtifactProviderService = mockk<GithubArtifactProviderService>()
    private val artifactMapper = mockk<GithubArtifactMapper>()
    private val failedMapper = mockk<GithubArtifactFailedMapper>()
    private val githubIngestionRunService = mockk<GithubIngestionRunService>()
    private val failedArtifactService = mockk<FailedArtifactService>()
    private val listener = GithubFileListener(
        githubArtifactProviderService,
        artifactMapper,
        failedMapper,
        githubIngestionRunService,
        failedArtifactService,
    )

    @Test
    fun `file fetched event maps and ingests artifact`() {
        val event = fileFetchedEvent()
        val command = artifactCommand(event.transactionId)
        every { artifactMapper.toCommand(event) } returns command
        every { githubArtifactProviderService.persistArtifact(command) } just runs

        listener.on(event)

        verify(exactly = 1) { githubArtifactProviderService.persistArtifact(command) }
    }

    @Test
    fun `file fetch failed event maps and records failed artifact`() {
        val event = GithubFileFetchFailedEvent(
            transactionId = UUID.randomUUID(),
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
            reason = "Missing",
        )
        val command = ArtifactFailedCommand(
            transactionId = event.transactionId,
            sourceId = "github:owner/repo:FILE:src/main/App.kt",
            sourceUrl = null,
            artifactType = ArtifactType.FILE,
            reason = "Missing",
            metadata = GithubArtifactMetadata(
                repositoryId = repositoryId,
                repositoryFullName = "owner/repo",
            ),
        )
        every { failedMapper.toCommand(event) } returns command
        every { failedArtifactService.addFailedArtifact(command) } just runs

        listener.on(event)

        verify(exactly = 1) { failedArtifactService.addFailedArtifact(command) }
    }

    @Test
    fun `files completed event marks files phase finished`() {
        val runId = UUID.randomUUID()
        every { githubIngestionRunService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(
            GithubFilesFetchCompletedEvent(
                transactionId = runId,
                repositoryOwner = "owner",
                repositoryName = "repo",
            ),
        )

        verify(exactly = 1) {
            githubIngestionRunService.markFetchPhaseFinished(runId, FinishedTypes.FILES)
        }
    }

    @Test
    fun `files failed event marks files phase finished`() {
        val runId = UUID.randomUUID()
        every { githubIngestionRunService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(
            GithubFilesFetchFailedEvent(
                transactionId = runId,
                repositoryOwner = "owner",
                repositoryName = "repo",
                reason = "Git failed",
            ),
        )

        verify(exactly = 1) {
            githubIngestionRunService.markFetchPhaseFinished(runId, FinishedTypes.FILES)
        }
    }

    @Test
    fun `file deleted event un-ingests file artifact`() {
        val event = GithubFileDeletedEvent(
            transactionId = UUID.randomUUID(),
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
        )
        every { githubArtifactProviderService.deleteFileArtifact(event) } just runs

        listener.on(event)

        verify(exactly = 1) { githubArtifactProviderService.deleteFileArtifact(event) }
    }

    private fun fileFetchedEvent() = GithubFileFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = repositoryId,
        repositoryOwner = "owner",
        repositoryName = "repo",
        path = "src/main/App.kt",
        content = "content",
        sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
    )

    private fun artifactCommand(runId: UUID) = GithubArtifactCommand(
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
        metadata = GithubArtifactMetadata(
            repositoryId = repositoryId,
            repositoryFullName = "owner/repo",
        ),
    )
}
