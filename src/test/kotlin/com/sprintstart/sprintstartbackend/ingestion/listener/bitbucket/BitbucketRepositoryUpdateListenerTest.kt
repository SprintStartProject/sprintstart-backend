package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class BitbucketRepositoryUpdateListenerTest {
    private val ingestionRunLifeCycleService = mockk<IngestionRunLifeCycleService>()
    private val listener = BitbucketRepositoryUpdateListener(ingestionRunLifeCycleService)

    @Test
    fun `update started opens a connected run attributed to the repository`() {
        val runId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs

        listener.on(
            BitbucketRepositoryUpdateStartedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                workspace = "sprintstart",
                slug = "backend",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startOrUpdateRun(
                transactionId = runId,
                sourceSystem = SourceSystem.BITBUCKET,
                status = IngestionRunStatus.CONNECTED,
                sourceInstanceId = repositoryId,
                sourceInstanceRef = "sprintstart/backend",
            )
        }
    }

    @Test
    fun `update failed fails the run with the reported reason`() {
        val runId = UUID.randomUUID()
        val repositoryId = UUID.randomUUID()
        every { ingestionRunLifeCycleService.startOrUpdateRun(any(), any(), any(), any(), any(), any()) } just runs

        listener.on(
            BitbucketRepositoryUpdateFailedEvent(
                transactionId = runId,
                repositoryId = repositoryId,
                workspace = "sprintstart",
                slug = "backend",
                reason = "database down",
            ),
        )

        verify(exactly = 1) {
            ingestionRunLifeCycleService.startOrUpdateRun(
                transactionId = runId,
                sourceSystem = SourceSystem.BITBUCKET,
                status = IngestionRunStatus.FAILED,
                failureReason = "database down",
                sourceInstanceId = repositoryId,
                sourceInstanceRef = "sprintstart/backend",
            )
        }
    }
}
