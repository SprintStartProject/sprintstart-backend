package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.RepositoryConnectionOutcome
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubRepositoryConnectionOrchestratorTest {
    private val connectorService = mockk<GithubConnectorService>()
    private val orchestrator = GithubRepositoryConnectionOrchestrator(connectorService)

    @Test
    fun `connectRepositoriesIfExist connects each repository and returns a map of transaction ids`() = runBlocking {
        val projectId = UUID.randomUUID()
        val repo1 = ConnectRepositoryRequest(
            owner = "owner1",
            name = "repo1",
            tokenName = "pat",
            projectId = projectId,
        )
        val repo2 = ConnectRepositoryRequest(
            owner = "owner2",
            name = "repo2",
            tokenName = "pat",
            projectId = projectId,
        )
        val request = ConnectRepositoriesRequest(listOf(repo1, repo2))
        val transactionId1 = UUID.randomUUID()
        val transactionId2 = UUID.randomUUID()

        coEvery { connectorService.connectRepositoryIfNecessary("auth-id", repo1) } returns
            RepositoryConnectionOutcome(transactionId1, wasReused = false)
        coEvery { connectorService.connectRepositoryIfNecessary("auth-id", repo2) } returns
            RepositoryConnectionOutcome(transactionId2, wasReused = true)

        val result = orchestrator.connectRepositoriesIfExist("auth-id", request)

        assertThat(result.transactionIdsByRepositoryId)
            .containsEntry("owner1/repo1", transactionId1)
            .containsEntry("owner2/repo2", transactionId2)
        // Only the reused one is reported as such, so the caller knows which repositories
        // started no ingestion run and have no progress to poll.
        assertThat(result.reusedRepositoryIds).containsExactly("owner2/repo2")
        coVerify { connectorService.connectRepositoryIfNecessary("auth-id", repo1) }
        coVerify { connectorService.connectRepositoryIfNecessary("auth-id", repo2) }
    }
}
