package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * The narrow cursor and state writes are only as correct as their JPQL: a wrong column would
 * silently write a neighbouring one, resurrecting the last-writer-wins clobbering the writes exist
 * to prevent. A mocked repository cannot see that; the queries have to meet a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class BitbucketConnectionRepositoryTest {
    @Autowired
    private lateinit var repository: BitbucketConnectionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `updateConnectionState writes only the state`() {
        val id = store().id

        repository.updateConnectionState(id, ConnectionState.FAILED)

        val stored = reloaded(id)
        assertThat(stored.connectionState).isEqualTo(ConnectionState.FAILED)
        assertThat(stored.lastSha).isEqualTo("sha-1")
        assertThat(stored.lastCommitsSyncedSha).isEqualTo("sha-0")
        assertThat(stored.sourceEnabled).isTrue()
    }

    @Test
    fun `updateFileCursor advances only the file cursor`() {
        val id = store().id

        repository.updateFileCursor(id, "sha-2")

        val stored = reloaded(id)
        assertThat(stored.lastSha).isEqualTo("sha-2")
        assertThat(stored.lastCommitsSyncedSha).isEqualTo("sha-0")
        assertThat(stored.lastPullRequestsSyncAt).isNull()
        assertThat(stored.sourceEnabled).isTrue()
        assertThat(stored.connectionState).isEqualTo(ConnectionState.UP_TO_DATE)
    }

    @Test
    fun `updateCommitsCursor advances only the commit cursor`() {
        val id = store().id

        repository.updateCommitsCursor(id, "sha-2")

        val stored = reloaded(id)
        assertThat(stored.lastCommitsSyncedSha).isEqualTo("sha-2")
        assertThat(stored.lastSha).isEqualTo("sha-1")
    }

    @Test
    fun `updatePullRequestsCursor writes the sync instant`() {
        val id = store().id
        val syncedAt = Instant.parse("2026-03-01T10:00:00Z")

        repository.updatePullRequestsCursor(id, syncedAt)

        val stored = reloaded(id)
        assertThat(stored.lastPullRequestsSyncAt).isEqualTo(syncedAt)
        assertThat(stored.lastSha).isEqualTo("sha-1")
    }

    @Test
    fun `findAllByWorkspace returns only that workspace`() {
        val api = store(workspace = "acme", slug = "api")
        val web = store(workspace = "acme", slug = "web")
        store(workspace = "other", slug = "api")

        assertThat(repository.findAllByWorkspace("acme").map { it.id })
            .containsExactlyInAnyOrder(api.id, web.id)
    }

    @Test
    fun `findAllByProjectId returns only linked connections`() {
        val projectId = UUID.randomUUID()
        val linked = store(workspace = "acme", slug = "api", projectIds = mutableSetOf(projectId, UUID.randomUUID()))
        store(workspace = "acme", slug = "web", projectIds = mutableSetOf(UUID.randomUUID()))

        assertThat(repository.findAllByProjectId(projectId).map { it.id }).containsExactly(linked.id)
    }

    @Test
    fun `findByWorkspaceAndSlug resolves one connection`() {
        val wanted = store(workspace = "acme", slug = "api")
        store(workspace = "acme", slug = "web")

        assertThat(repository.findByWorkspaceAndSlug("acme", "api")?.id).isEqualTo(wanted.id)
        assertThat(repository.findByWorkspaceAndSlug("acme", "missing")).isNull()
    }

    @Test
    fun `findAllByWorkspaceAndSlugIn matches the workspace's slugs only`() {
        val api = store(workspace = "acme", slug = "api")
        store(workspace = "acme", slug = "web")
        store(workspace = "other", slug = "api")

        val found = repository.findAllByWorkspaceAndSlugIn("acme", listOf("api", "missing"))

        assertThat(found.map { it.id }).containsExactly(api.id)
    }

    private fun store(
        workspace: String = "acme",
        slug: String = "api",
        projectIds: MutableSet<UUID> = mutableSetOf(),
    ): BitbucketConnection {
        val connection = BitbucketConnection(
            workspace = workspace,
            slug = slug,
            credentialAuthId = "auth-id",
            credentialName = "team-token",
            lastSha = "sha-1",
            lastCommitsSyncedSha = "sha-0",
            projectIdsInternal = projectIds,
        )
        entityManager.persist(connection)
        entityManager.flush()
        entityManager.clear()
        return connection
    }

    private fun reloaded(id: UUID): BitbucketConnection {
        entityManager.flush()
        entityManager.clear()
        return entityManager.find(BitbucketConnection::class.java, id)
    }
}
