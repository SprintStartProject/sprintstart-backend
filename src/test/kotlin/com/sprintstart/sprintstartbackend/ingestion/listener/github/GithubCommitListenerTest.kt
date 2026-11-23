package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.CommitFileChangesCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.CommitFileChangesMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.CommitFileChangeService
import com.sprintstart.sprintstartbackend.ingestion.service.FailedArtifactService
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GithubCommitListenerTest {
    private val providerService = mockk<GithubArtifactProviderService>(relaxed = true)
    private val artifactMapper = mockk<GithubArtifactMapper>()
    private val fileChangeService = mockk<CommitFileChangeService>(relaxed = true)
    private val fileChangesMapper = mockk<CommitFileChangesMapper>()

    private val listener = GithubCommitListener(
        githubArtifactProviderService = providerService,
        githubArtifactMapper = artifactMapper,
        githubArtifactFailedMapper = mockk<GithubArtifactFailedMapper>(),
        githubIngestionRunService = mockk<GithubIngestionRunService>(),
        failedArtifactService = mockk<FailedArtifactService>(),
        commitFileChangeService = fileChangeService,
        commitFileChangesMapper = fileChangesMapper,
    )

    private val event = GithubCommitFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = UUID.randomUUID(),
        repositoryOwner = "acme",
        repositoryName = "backend",
        author = "Ada",
        authorEmail = "ada@example.com",
        date = Instant.parse("2026-03-04T05:06:07Z"),
        sha = "abc123",
        msg = "Fix it",
        files = CommitFiles.Listed(emptyList()),
    )

    @Test
    fun `records a commit's files after storing the commit`() {
        val artifactCommand = mockk<GithubArtifactCommand>()
        val fileChanges = mockk<CommitFileChangesCommand>()
        every { artifactMapper.toCommand(event) } returns artifactCommand
        every { fileChangesMapper.toCommand(event) } returns fileChanges

        listener.on(event)

        verifyOrder {
            providerService.persistArtifact(artifactCommand)
            fileChangeService.record(fileChanges)
        }
    }
}
