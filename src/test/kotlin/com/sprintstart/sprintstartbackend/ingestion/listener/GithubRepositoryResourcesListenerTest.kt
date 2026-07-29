package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryResourcesListener
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryResourcesListenerTest {
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>()
    private val listener = GithubRepositoryResourcesListener(ingestionRunLifeCycleService)

    @Test
    fun `fetching started event marks run as running and links its repository`() {
        val runId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryResourcesFetchingStartedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                owner = "owner",
                name = "repo",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startOrUpdateRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.RUNNING,
                failureReason = null,
                sourceInstanceId = repositoryId,
                sourceInstanceRef = "owner/repo",
            )
        }
    }
}
