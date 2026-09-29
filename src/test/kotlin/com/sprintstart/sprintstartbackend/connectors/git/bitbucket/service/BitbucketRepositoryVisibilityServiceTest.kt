package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class BitbucketRepositoryVisibilityServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val credentialApi = mockk<AtlassianCredentialApi>()
    private val bitbucketClient = mockk<BitbucketClient>()
    private val service = BitbucketRepositoryVisibilityService(
        connectionRepository = connectionRepository,
        credentialApi = credentialApi,
        bitbucketClient = bitbucketClient,
    )

    private val repositoryId = UUID.randomUUID()

    @Test
    fun `accepts a caller whose first stored credential can see the repository`() = runTest {
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection())
        every { credentialApi.findAllSecretsByAuthId("auth-id") } returns listOf(secret("token-a"))
        coEvery { bitbucketClient.repositoryExists("sprintstart", "backend", "token-a") } returns true

        service.requireCallerCanSeeConnection("auth-id", repositoryId)
    }

    @Test
    fun `tries the next stored credential when one cannot see the repository`() = runTest {
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection())
        every { credentialApi.findAllSecretsByAuthId("auth-id") } returns listOf(secret("token-a"), secret("token-b"))
        coEvery { bitbucketClient.repositoryExists("sprintstart", "backend", "token-a") } returns false
        coEvery { bitbucketClient.repositoryExists("sprintstart", "backend", "token-b") } returns true

        service.requireCallerCanSeeConnection("auth-id", repositoryId)

        coVerify(exactly = 1) { bitbucketClient.repositoryExists("sprintstart", "backend", "token-a") }
    }

    @Test
    fun `refuses a caller none of whose credentials can see the repository`() = runTest {
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection())
        every { credentialApi.findAllSecretsByAuthId("auth-id") } returns listOf(secret("token-a"))
        coEvery { bitbucketClient.repositoryExists("sprintstart", "backend", "token-a") } returns false

        assertThrows<BitbucketRepositoryConnectionNotFoundException> {
            service.requireCallerCanSeeConnection("auth-id", repositoryId)
        }
    }

    @Test
    fun `refuses a caller without any stored credential`() = runTest {
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection())
        every { credentialApi.findAllSecretsByAuthId("auth-id") } returns emptyList()

        assertThrows<BitbucketRepositoryConnectionNotFoundException> {
            service.requireCallerCanSeeConnection("auth-id", repositoryId)
        }

        coVerify(exactly = 0) { bitbucketClient.repositoryExists(any(), any(), any()) }
    }

    /** An unknown connection gets the same answer as an invisible one, so the two stay indistinguishable. */
    @Test
    fun `refuses an unknown connection with the same answer as an invisible one`() = runTest {
        every { connectionRepository.findById(repositoryId) } returns Optional.empty()

        val unknown = assertThrows<BitbucketRepositoryConnectionNotFoundException> {
            service.requireCallerCanSeeConnection("auth-id", repositoryId)
        }
        coEvery { bitbucketClient.repositoryExists(any(), any(), any()) } returns false
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection())
        every { credentialApi.findAllSecretsByAuthId("auth-id") } returns listOf(secret("token-a"))

        val invisible = assertThrows<BitbucketRepositoryConnectionNotFoundException> {
            service.requireCallerCanSeeConnection("auth-id", repositoryId)
        }

        assertThat(unknown.message).isEqualTo(invisible.message)
    }

    private fun connection() = BitbucketConnection(
        workspace = "sprintstart",
        slug = "backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )

    private fun secret(token: String) = AtlassianCredentialSecret(userEmail = "user@example.com", apiToken = token)
}
