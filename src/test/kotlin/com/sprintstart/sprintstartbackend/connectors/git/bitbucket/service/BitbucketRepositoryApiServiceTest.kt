package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BitbucketRepositoryApiServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val service = BitbucketRepositoryApiService(connectionRepository)

    private val repositoryId = UUID.randomUUID()

    @Test
    fun `getRepositoryProjectIdsById returns the connection's project ids`() {
        val projectIds = setOf(UUID.randomUUID(), UUID.randomUUID())
        val connection = connection(projectIds = projectIds.toMutableSet())
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection)

        assertThat(service.getRepositoryProjectIdsById(repositoryId)).isEqualTo(projectIds)
    }

    @Test
    fun `getRepositoryProjectIdsById throws when the repository connection does not exist`() {
        every { connectionRepository.findById(repositoryId) } returns Optional.empty()

        assertThrows<NoSuchElementException> {
            service.getRepositoryProjectIdsById(repositoryId)
        }
    }

    @Test
    fun `getRepositoryIdByWorkspaceAndSlug returns the connection id`() {
        val id = UUID.randomUUID()
        every { connectionRepository.findByWorkspaceAndSlug("sprintstart", "backend") } returns
            mockk { every { this@mockk.id } returns id }

        assertThat(service.getRepositoryIdByWorkspaceAndSlug("sprintstart", "backend")).isEqualTo(id)
    }

    @Test
    fun `getRepositoryIdByWorkspaceAndSlug returns null when no connection exists`() {
        every { connectionRepository.findByWorkspaceAndSlug("sprintstart", "backend") } returns null

        assertThat(service.getRepositoryIdByWorkspaceAndSlug("sprintstart", "backend")).isNull()
    }

    @Test
    fun `getRepositoryIdsByProject maps connections to their ids`() {
        val projectId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { connectionRepository.findAllByProjectId(projectId) } returns listOf(
            mockk { every { this@mockk.id } returns first },
            mockk { every { this@mockk.id } returns second },
        )

        assertThat(service.getRepositoryIdsByProject(projectId)).containsExactly(first, second)
    }

    @Test
    fun `getSourceInstances derives the browser url and status of a connected repository`() {
        val syncAt = Instant.parse("2026-07-06T12:00:00Z")
        val linked = connection().apply {
            lastPullRequestsSyncAt = syncAt
        }
        every { connectionRepository.findAll() } returns listOf(linked)

        val result = service.getSourceInstances().single()

        assertThat(result.repositoryId).isEqualTo(repositoryId)
        assertThat(result.workspace).isEqualTo("sprintstart")
        assertThat(result.slug).isEqualTo("backend")
        assertThat(result.sourceUrl).isEqualTo("https://bitbucket.org/sprintstart/backend")
        assertThat(result.status).isEqualTo("CONNECTED")
        assertThat(result.enabled).isTrue()
        assertThat(result.lastPullRequestsSyncAt).isEqualTo(syncAt)
    }

    /**
     * A paused repository must not be reported as actively connected, whatever its last run did.
     */
    @Test
    fun `getSourceInstances reports a disabled repository as DISABLED regardless of its connection state`() {
        val paused = connection().apply {
            sourceEnabled = false
            connectionState = ConnectionState.FAILED
        }
        every { connectionRepository.findAll() } returns listOf(paused)

        val result = service.getSourceInstances().single()

        assertThat(result.status).isEqualTo("DISABLED")
        assertThat(result.enabled).isFalse()
    }

    @Test
    fun `getSourceInstances orders repositories by workspace then slug`() {
        every { connectionRepository.findAll() } returns listOf(
            connection(workspace = "zeta", slug = "alpha"),
            connection(workspace = "acme", slug = "zulu"),
            connection(workspace = "acme", slug = "alpha"),
        )

        val result = service.getSourceInstances()

        assertThat(result.map { "${it.workspace}/${it.slug}" })
            .containsExactly("acme/alpha", "acme/zulu", "zeta/alpha")
    }

    @Test
    fun `getSourceInstances filters by project id when provided`() {
        val projectId = UUID.randomUUID()
        every { connectionRepository.findAllByProjectId(projectId) } returns listOf(connection())

        val result = service.getSourceInstances(projectId).single()

        assertThat(result.repositoryId).isEqualTo(repositoryId)
    }

    @Test
    fun `removeProjectFromAllRepositories drops the project from every linked connection`() {
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        val first = connection(projectIds = mutableSetOf(projectId, otherProjectId))
        val second = connection(projectIds = mutableSetOf(projectId))
        every { connectionRepository.findAllByProjectId(projectId) } returns listOf(first, second)
        val saved = slot<List<BitbucketConnection>>()
        every { connectionRepository.saveAll(capture(saved)) } answers { firstArg() }

        service.removeProjectFromAllRepositories(projectId)

        assertThat(first.projectIds).containsExactly(otherProjectId)
        assertThat(second.projectIds).isEmpty()
        assertThat(saved.captured).containsExactly(first, second)
    }

    /**
     * Deleting a project that no Bitbucket repository is linked to must leave every connection alone.
     * The empty lookup still reaches the batch write, so it is the write's contents that carry the
     * guarantee: nothing is passed to it.
     */
    @Test
    fun `removeProjectFromAllRepositories touches nothing when no connection is linked`() {
        val projectId = UUID.randomUUID()
        every { connectionRepository.findAllByProjectId(projectId) } returns emptyList()
        val saved = slot<List<BitbucketConnection>>()
        // saveAll is declared to return a mutable list, so the stub answers with one rather than the
        // captured value: a Kotlin empty list is not a java.util.List and would fail that cast.
        every { connectionRepository.saveAll(capture(saved)) } returns mutableListOf<BitbucketConnection>()

        service.removeProjectFromAllRepositories(projectId)

        assertThat(saved.captured).isEmpty()
    }

    private fun connection(
        id: UUID = repositoryId,
        workspace: String = "sprintstart",
        slug: String = "backend",
        projectIds: MutableSet<UUID> = mutableSetOf(),
    ) =
        BitbucketConnection(
            id = id,
            workspace = workspace,
            slug = slug,
            credentialAuthId = "auth-id",
            credentialName = "team-token",
            projectIdsInternal = projectIds,
        )
}
