package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryUpdateListener
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
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
    private val listener = GithubRepositoryUpdateListener(ingestionRunLifeCycleService)

    @Test
    fun `update started event starts connected github run`() {
        val runId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startRun(any(), any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryUpdateStartedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.CONNECTED,
                failureReason = null,
            )
        }
    }

    @Test
    fun `update failed event starts failed github run with failure reason`() {
        val runId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startRun(any(), any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryUpdateFailedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
                reason = "Snapshot missing",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
                failureReason = "Snapshot missing",
            )
        }
    }
}
