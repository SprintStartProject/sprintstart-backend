package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFiles
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ReviewActivityCommand
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ReviewActivityMapper
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.ReviewActivityService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubPullRequestListenerTest {
    private val providerService = mockk<GithubArtifactProviderService>(relaxed = true)
    private val artifactMapper = mockk<GithubArtifactMapper>()
    private val reviewActivityService = mockk<ReviewActivityService>(relaxed = true)
    private val reviewActivityMapper = mockk<ReviewActivityMapper>()

    private val listener = GithubPullRequestListener(
        githubArtifactProviderService = providerService,
        githubArtifactMapper = artifactMapper,
        githubIngestionRunService = mockk<GithubIngestionRunService>(),
        reviewActivityService = reviewActivityService,
        reviewActivityMapper = reviewActivityMapper,
    )

    private val event = GithubPullRequestFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = UUID.randomUUID(),
        repositoryOwner = "acme",
        repositoryName = "backend",
        number = 7,
        title = "Improve docs",
        body = null,
        state = "MERGED",
        createdAt = "2024-01-02T00:00:00Z",
        mergedAt = null,
        url = "https://github.com/acme/backend/pull/7",
        author = "bob",
        labels = null,
        reviews = null,
        comments = null,
        reviewThreads = null,
        files = GithubPullRequestFiles.Listed(emptyList()),
    )

    @Test
    fun `records a pull request's reviews after storing the pull request`() {
        val artifactCommand = mockk<GithubArtifactCommand>()
        val reviews = mockk<ReviewActivityCommand>()
        every { artifactMapper.toCommand(event) } returns artifactCommand
        every { reviewActivityMapper.toCommand(event) } returns reviews

        listener.on(event)

        verifyOrder {
            providerService.persistArtifact(artifactCommand)
            reviewActivityService.record(reviews)
        }
    }
}
