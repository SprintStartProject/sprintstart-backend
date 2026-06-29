package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryResourcesListenerTest {
    private val artifactIngestionService = mockk<ArtifactIngestionService>()
    private val listener = GithubRepositoryResourcesListener(artifactIngestionService)

    @Test
    fun `fetching started event marks run as running`() {
        val runId = UUID.randomUUID()
        every { artifactIngestionService.updateRunStatus(any(), any()) } just runs

        listener.on(
            GithubRepositoryResourcesFetchingStartedEvent(
                transactionId = runId,
                owner = "owner",
                name = "repo",
            ),
        )

        verify(exactly = 1) {
            artifactIngestionService.updateRunStatus(
                transactionId = runId,
                status = IngestionRunStatus.RUNNING,
            )
        }
    }
}
