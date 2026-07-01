package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.github.external.events.update.GithubRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.update.GithubRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryUpdateListener
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryUpdateListenerTest {
    private val artifactIngestionService = mockk<ArtifactIngestionService>()
    private val listener = GithubRepositoryUpdateListener(artifactIngestionService)

    @Test
    fun `update started event starts connected github run`() {
        val runId = UUID.randomUUID()
        every { artifactIngestionService.startRun(any(), any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryUpdateStartedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
            ),
        )

        verify(exactly = 1) {
            artifactIngestionService.startRun(
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
        every { artifactIngestionService.startRun(any(), any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryUpdateFailedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
                reason = "Snapshot missing",
            ),
        )

        verify(exactly = 1) {
            artifactIngestionService.startRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
                failureReason = "Snapshot missing",
            )
        }
    }
}
