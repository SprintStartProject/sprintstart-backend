package com.sprintstart.sprintstartbackend.canonical.listener

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryConnectionListenerTest {
    private val artifactIngestionService = mockk<ArtifactIngestionService>()
    private val listener = GithubRepositoryConnectionListener(artifactIngestionService)

    @Test
    fun `initiated event starts connected github run`() {
        val runId = UUID.randomUUID()
        every { artifactIngestionService.startRun(any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryConnectionInitiatedEvent(
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
            )
        }
    }

    @Test
    fun `failed initiation event starts failed github run`() {
        val runId = UUID.randomUUID()
        every { artifactIngestionService.startRun(any(), any(), any()) } just runs

        listener.on(
            GithubRepositoryConnectionInitiationFailedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
                reason = "Token rejected",
            ),
        )

        verify(exactly = 1) {
            artifactIngestionService.startRun(
                transactionId = runId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
            )
        }
    }
}
