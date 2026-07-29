package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GithubRepositoryApiServiceTest {
    private val repository = mockk<GithubRepositoryConnectionRepository>()
    private val service = GithubRepositoryApiService(repository)

    @Test
    fun `getRepositoryIdByOwnerAndName returns the connection id`() {
        val repositoryId = UUID.randomUUID()
        every { repository.findByOwnerAndName("owner", "repo") } returns
            mockk { every { id } returns repositoryId }

        assertThat(service.getRepositoryIdByOwnerAndName("owner", "repo")).isEqualTo(repositoryId)
    }

    @Test
    fun `getRepositoryIdByOwnerAndName returns null when no connection exists`() {
        every { repository.findByOwnerAndName("owner", "repo") } returns null

        assertThat(service.getRepositoryIdByOwnerAndName("owner", "repo")).isNull()
    }

    @Test
    fun `getRepositoryIdsByProject maps connections to their ids`() {
        val projectId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { repository.findAllByProjectId(projectId) } returns listOf(
            mockk { every { id } returns first },
            mockk { every { id } returns second },
        )

        assertThat(service.getRepositoryIdsByProject(projectId)).containsExactly(first, second)
    }

    @Test
    fun `getSourceInstances maps enabled connection with snapshot timestamps and sorts by owner and name`() {
        val commitsAt = Instant.parse("2026-07-06T10:00:00Z")
        val issuesAt = Instant.parse("2026-07-06T11:00:00Z")
        val prAt = Instant.parse("2026-07-06T12:00:00Z")
        val connected = connection(
            id = UUID.randomUUID(),
            owner = "beta",
            name = "repo",
            sourceEnabled = true,
            connectionState = ConnectionState.UP_TO_DATE,
            snapshot = mockk {
                every { lastCommitsSyncAt } returns commitsAt
                every { lastIssuesSyncAt } returns issuesAt
                every { lastPullRequestsSyncAt } returns prAt
            },
        )
        val disabled = connection(
            id = UUID.randomUUID(),
            owner = "alpha",
            name = "repo",
            sourceEnabled = false,
            connectionState = ConnectionState.UP_TO_DATE,
            snapshot = null,
        )
        every { repository.findAll() } returns listOf(connected, disabled)

        val result = service.getSourceInstances()

        assertThat(result.map { it.owner }).containsExactly("alpha", "beta")
        val alpha = result.first()
        assertThat(alpha.status).isEqualTo("DISABLED")
        assertThat(alpha.enabled).isFalse()
        assertThat(alpha.lastCommitsSyncAt).isNull()
        val beta = result.last()
        assertThat(beta.status).isEqualTo("CONNECTED")
        assertThat(beta.enabled).isTrue()
        assertThat(beta.lastCommitsSyncAt).isEqualTo(commitsAt)
        assertThat(beta.lastIssuesSyncAt).isEqualTo(issuesAt)
        assertThat(beta.lastPullRequestsSyncAt).isEqualTo(prAt)
    }

    @Test
    fun `getSourceInstances filters by project id when provided`() {
        val projectId = UUID.randomUUID()
        val connection = connection(
            id = UUID.randomUUID(),
            owner = "owner",
            name = "repo",
            sourceEnabled = true,
            connectionState = ConnectionState.OUT_OF_DATE,
            snapshot = null,
        )
        every { repository.findAllByProjectId(projectId) } returns listOf(connection)

        val result = service.getSourceInstances(projectId).single()

        assertThat(result.status).isEqualTo("OUT_OF_DATE")
    }

    @Test
    fun `removeProjectFromAllRepositories drops the project from every linked connection`() {
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        val firstProjects = mutableSetOf(projectId, otherProjectId)
        val secondProjects = mutableSetOf(projectId)
        val first = mockk<GithubRepositoryConnection> { every { projectIdsInternal } returns firstProjects }
        val second = mockk<GithubRepositoryConnection> { every { projectIdsInternal } returns secondProjects }
        every { repository.findAllByProjectId(projectId) } returns listOf(first, second)
        val saved = slot<List<GithubRepositoryConnection>>()
        every { repository.saveAll(capture(saved)) } answers { firstArg() }

        service.removeProjectFromAllRepositories(projectId)

        assertThat(firstProjects).containsExactly(otherProjectId)
        assertThat(secondProjects).isEmpty()
        assertThat(saved.captured).containsExactly(first, second)
    }

    private fun connection(
        id: UUID,
        owner: String,
        name: String,
        sourceEnabled: Boolean,
        connectionState: ConnectionState,
        snapshot: GithubRepositorySnapshot?,
    ): GithubRepositoryConnection =
        mockk {
            every { this@mockk.id } returns id
            every { this@mockk.owner } returns owner
            every { this@mockk.name } returns name
            every { this@mockk.sourceEnabled } returns sourceEnabled
            every { this@mockk.connectionState } returns connectionState
            every { this@mockk.snapshot } returns snapshot
        }
}
