package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.BitbucketArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.CommitFileChangesCommand
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.CommitFileChangesMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.CommitFileChangeService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BitbucketCommitListenerTest {
    private val providerService = mockk<BitbucketArtifactProviderService>(relaxed = true)
    private val artifactMapper = mockk<BitbucketArtifactMapper>()
    private val fileChangeService = mockk<CommitFileChangeService>(relaxed = true)
    private val fileChangesMapper = mockk<CommitFileChangesMapper>()

    private val listener = BitbucketCommitListener(
        bitbucketArtifactProviderService = providerService,
        bitbucketArtifactMapper = artifactMapper,
        bitbucketIngestionRunService = mockk<BitbucketIngestionRunService>(),
        commitFileChangeService = fileChangeService,
        commitFileChangesMapper = fileChangesMapper,
    )

    private val event = BitbucketCommitFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = UUID.randomUUID(),
        workspace = "acme",
        slug = "backend",
        author = "Ada",
        authorEmail = "ada@example.com",
        committedAt = Instant.parse("2026-03-04T05:06:07Z"),
        sha = "abc123",
        subject = "Fix it",
        sourceUrl = "https://bitbucket.org/acme/backend/commits/abc123",
        files = CommitFiles.Listed(emptyList()),
    )

    @Test
    fun `records a commit's files after storing the commit`() {
        val artifactCommand = mockk<BitbucketArtifactCommand>()
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
