package com.sprintstart.sprintstartbackend.connectors.git.github.repository

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * The narrow cursor writes are only as correct as their JPQL: a wrong column would silently write
 * a neighbouring one, resurrecting the lost update they exist to prevent. A mocked repository
 * cannot see that; the queries have to meet a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class GithubRepositoryConnectionRepositoryTest {
    @Autowired
    private lateinit var repository: GithubRepositoryConnectionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `updateFileCursor advances only the file cursor`() {
        val id = store().id

        repository.updateFileCursor(id, "sha-2")

        val stored = reloaded(id)
        assertThat(stored.lastSha).isEqualTo("sha-2")
        assertThat(stored.lastCommitsSyncedSha).isEqualTo("sha-0")
        assertThat(stored.sourceEnabled).isTrue()
        assertThat(stored.connectionState).isEqualTo(ConnectionState.UP_TO_DATE)
    }

    @Test
    fun `updateCommitsCursor advances only the commit cursor and keeps the snapshot`() {
        val id = store().id

        repository.updateCommitsCursor(id, "sha-2")

        val stored = reloaded(id)
        assertThat(stored.lastCommitsSyncedSha).isEqualTo("sha-2")
        assertThat(stored.lastSha).isEqualTo("sha-1")
        assertThat(stored.sourceEnabled).isTrue()
        assertThat(stored.snapshot?.id).isEqualTo(id)
    }

    private fun store(): GithubRepositoryConnection {
        val user = GithubUser(id = GithubUserPat("auth-id", "token-name"), token = "test-token")
        entityManager.persist(user)

        val connection = GithubRepositoryConnection(
            id = UUID.randomUUID(),
            owner = "owner",
            name = "repo",
            user = user,
            lastSha = "sha-1",
            lastCommitsSyncedSha = "sha-0",
        )
        connection.snapshot = GithubRepositorySnapshot(repository = connection)
        entityManager.persist(connection)
        entityManager.flush()
        entityManager.clear()
        return connection
    }

    private fun reloaded(id: UUID): GithubRepositoryConnection {
        entityManager.flush()
        entityManager.clear()
        return entityManager.find(GithubRepositoryConnection::class.java, id)
    }
}
