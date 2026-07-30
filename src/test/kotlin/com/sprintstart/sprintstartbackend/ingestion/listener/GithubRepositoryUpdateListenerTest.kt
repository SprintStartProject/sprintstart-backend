package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryUpdateListener
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryUpdateListenerTest {
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>()
    private val githubRepositoryApi = mockk<GithubRepositoryApi>()
    private val listener =
        GithubRepositoryUpdateListener(ingestionRunLifeCycleService, githubRepositoryApi)

    @Test
    fun `update started event starts connected github run with resolved repository metadata`() {
        val runId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("owner", "repo") } returns repositoryId

        listener.on(
            GithubRepositoryUpdateStartedEvent(
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
    fun `update failed event starts failed github run with failure reason and repository metadata`() {
        val runId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("owner", "repo") } returns repositoryId

        listener.on(
            GithubRepositoryUpdateFailedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
                reason = "Snapshot missing",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startOrUpdateRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
                failureReason = "Snapshot missing",
                sourceInstanceId = repositoryId,
                sourceInstanceRef = "owner/repo",
            )
        }
    }
}
