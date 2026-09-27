package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketGitProvider
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BitbucketRepositoryCoordinatesFactoryTest {
    private val credentialApi = mockk<AtlassianCredentialApi>()
    private val factory = BitbucketRepositoryCoordinatesFactory(credentialApi, BitbucketGitProvider())

    @Test
    fun `maps a connection onto bitbucket clone coordinates`() {
        every { credentialApi.findSecret("auth-id", "team-token") } returns
            AtlassianCredentialSecret(userEmail = "user@example.com", apiToken = "api-token")

        val coordinates = factory.of(connection())

        assertThat(coordinates).isEqualTo(
            GitRepositoryCoordinates(
                host = "bitbucket.org",
                namespace = "sprintstart",
                name = "sprintstart-backend",
                username = "x-bitbucket-api-token-auth",
                secret = "api-token",
            ),
        )
    }

    @Test
    fun `resolves the credential of the connection rather than storing the token`() {
        every { credentialApi.findSecret(any(), any()) } returns
            AtlassianCredentialSecret(userEmail = "user@example.com", apiToken = "api-token")

        factory.of(connection())

        verify(exactly = 1) { credentialApi.findSecret("auth-id", "team-token") }
    }

    @Test
    fun `fails when the credential of the connection no longer exists`() {
        every { credentialApi.findSecret(any(), any()) } returns null

        assertThatThrownBy { factory.of(connection()) }
            .isInstanceOf(AtlassianCredentialNotFoundException::class.java)
    }

    private fun connection() = BitbucketConnection(
        workspace = "sprintstart",
        slug = "sprintstart-backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )
}
