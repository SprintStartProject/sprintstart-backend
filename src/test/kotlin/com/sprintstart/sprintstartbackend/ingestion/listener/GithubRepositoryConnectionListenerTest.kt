package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryConnectionListener
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryConnectionListenerTest {
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>()
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()
    private val listener =
        GithubRepositoryConnectionListener(ingestionRunLifeCycleService, githubRepositoryApi)

    @Test
    fun `initiated event starts connected github run with resolved repository metadata`() {
        val runId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("owner", "repo") } returns repositoryId

        listener.on(
            GithubRepositoryConnectionInitiatedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startOrUpdateRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.CONNECTED,
                sourceInstanceId = repositoryId,
                sourceInstanceRef = "owner/repo",
            )
        }
    }

    @Test
    fun `failed initiation event starts failed github run and leaves repository id null when unknown`() {
        val runId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("owner", "repo") } returns null

        listener.on(
            GithubRepositoryConnectionInitiationFailedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
                reason = "Token rejected",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startOrUpdateRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
                failureReason = "Token rejected",
                sourceInstanceId = null,
                sourceInstanceRef = "owner/repo",
            )
        }
    }
}
