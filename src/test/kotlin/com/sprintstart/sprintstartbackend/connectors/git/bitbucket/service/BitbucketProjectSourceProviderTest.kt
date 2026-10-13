package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BitbucketProjectSourceProviderTest {
    private val repository = mockk<BitbucketConnectionRepository>()
    private val provider = BitbucketProjectSourceProvider(repository)

    private val projectId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()

    @Test
    fun `maps a linked connection to a project source summary`() {
        every { repository.findAllByProjectId(projectId) } returns listOf(connection())

        val source = provider.findSourcesByProjectId(projectId).single()

        assertThat(source.id).isEqualTo(repositoryId.toString())
        assertThat(source.name).isEqualTo("sprintstart/backend")
        assertThat(source.type).isEqualTo("BITBUCKET")
        assertThat(source.status).isEqualTo("CONNECTED")
    }

    /**
     * A paused repository must be reported as disabled here too, not just in the ingestion status
     * view, so the two never disagree about the same connection.
     */
    @Test
    fun `reports a disabled connection as DISABLED regardless of its connection state`() {
        val paused = connection().apply {
            sourceEnabled = false
            connectionState = ConnectionState.FAILED
        }
        every { repository.findAllByProjectId(projectId) } returns listOf(paused)

        assertThat(provider.findSourcesByProjectId(projectId).single().status).isEqualTo("DISABLED")
    }

    @Test
    fun `returns no sources when the project has no Bitbucket connections`() {
        every { repository.findAllByProjectId(projectId) } returns emptyList()

        assertThat(provider.findSourcesByProjectId(projectId)).isEmpty()
    }

    private fun connection() = BitbucketConnection(
        id = repositoryId,
        workspace = "sprintstart",
        slug = "backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )
}
