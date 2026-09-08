package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataMember
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubOrgListenerTest {
    private val githubArtifactProviderService = mockk<GithubArtifactProviderService>()
    private val githubArtifactMapper = mockk<GithubArtifactMapper>()
    private val githubIngestionRunService = mockk<GithubIngestionRunService>()
    private val listener = GithubOrgListener(
        githubArtifactProviderService,
        githubArtifactMapper,
        githubIngestionRunService,
    )

    @Test
    fun `org metadata fetched event maps and persists artifact`() {
        val event = fetchedEvent()
        val command = mockk<GithubArtifactCommand>()
        every { githubArtifactMapper.toCommand(event) } returns command
        every { githubArtifactProviderService.persistArtifact(command) } just runs

        listener.on(event)

        verify(exactly = 1) { githubArtifactProviderService.persistArtifact(command) }
    }

    @Test
    fun `org metadata completed event marks org phase finished`() {
        val runId = UUID.randomUUID()
        every { githubIngestionRunService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(GithubOrgMetadataFetchingCompletedEvent(runId))

        verify(exactly = 1) {
            githubIngestionRunService.markFetchPhaseFinished(runId, FinishedTypes.ORG_METADATA)
        }
    }

    @Test
    fun `org metadata failed event marks org phase finished`() {
        val runId = UUID.randomUUID()
        every { githubIngestionRunService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(GithubOrgMetadataFetchingFailedEvent(runId, "boom"))

        verify(exactly = 1) {
            githubIngestionRunService.markFetchPhaseFinished(runId, FinishedTypes.ORG_METADATA)
        }
    }

    private fun fetchedEvent() = GithubOrgMetadataFetchedEvent(
        transactionId = UUID.randomUUID(),
        login = "octocat",
        name = "The Octocats",
        description = null,
        company = null,
        blog = null,
        location = null,
        email = null,
        publicRepos = null,
        privateRepos = null,
        teams = null,
        members = listOf(GithubOrgMetadataMember(login = "alice", url = "https://github.com/alice")),
    )
}
