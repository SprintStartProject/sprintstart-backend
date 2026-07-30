package com.sprintstart.sprintstartbackend.connectors.github.service

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

        coEvery { connectorService.connectRepositoryIfExists("auth-id", repo1) } returns transactionId1
        coEvery { connectorService.connectRepositoryIfExists("auth-id", repo2) } returns transactionId2

        val result = orchestrator.connectRepositoriesIfExist("auth-id", request)

        assertThat(result.transactionIdsByRepositoryId)
            .containsEntry("owner1/repo1", transactionId1)
            .containsEntry("owner2/repo2", transactionId2)
        coVerify { connectorService.connectRepositoryIfExists("auth-id", repo1) }
        coVerify { connectorService.connectRepositoryIfExists("auth-id", repo2) }
    }
}
