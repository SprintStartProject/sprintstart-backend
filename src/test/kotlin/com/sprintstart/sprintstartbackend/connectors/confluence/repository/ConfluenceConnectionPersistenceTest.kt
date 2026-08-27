package com.sprintstart.sprintstartbackend.connectors.confluence.repository

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ConfluenceConnectionPersistenceTest {
    @Autowired
    private lateinit var connectionRepository: ConfluenceSpaceConnectionRepository

    @Autowired
    private lateinit var credentialRepository: ConfluenceCredentialRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `connection and encrypted credential round-trip with project ownership`() {
        val projectId = UUID.randomUUID()
        val plaintextToken = "database-secret-token"
        val connection = connection(projectId, "123", listOf("10", "20"), listOf("20"))
        connection.configureCredential("fake-user@example.invalid", plaintextToken)

        val saved = connectionRepository.saveAndFlush(connection)
        entityManager.clear()

        val rawToken = jdbcTemplate.queryForObject(
            "SELECT api_token FROM confluence_credentials WHERE connection_id = ?",
            String::class.java,
            saved.id,
        )
        val loaded = connectionRepository.findByIdAndProjectId(saved.id, projectId)
        val loadedCredential = credentialRepository.findByConnectionIdAndConnectionProjectId(saved.id, projectId)

        assertThat(rawToken).isNotBlank().isNotEqualTo(plaintextToken)
        assertThat(loaded).isNotNull
        assertThat(loaded!!.projectId).isEqualTo(projectId)
        assertThat(loaded.baseUrl).isEqualTo("https://tenant.atlassian.net")
        assertThat(loaded.spaceId).isEqualTo("123")
        assertThat(loaded.spaceKey).isEqualTo("ENG")
        assertThat(loaded.pageAllowlist).containsExactly("10", "20")
        assertThat(loaded.pageDenylist).containsExactly("20")
        assertThat(loadedCredential!!.apiToken).isEqualTo(plaintextToken)
    }

    @Test
    fun `repository lookup remains scoped to the requested project`() {
        val ownerProjectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        val saved = connectionRepository.saveAndFlush(connection(ownerProjectId, "123"))
        entityManager.clear()

        assertThat(connectionRepository.findByIdAndProjectId(saved.id, ownerProjectId)).isNotNull
        assertThat(connectionRepository.findByIdAndProjectId(saved.id, otherProjectId)).isNull()
        assertThat(connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(otherProjectId)).isEmpty()
    }

    @Test
    fun `database rejects duplicate project tenant and space connections`() {
        val projectId = UUID.randomUUID()
        connectionRepository.saveAndFlush(connection(projectId, "123").withCredential("first-token"))

        assertThatThrownBy {
            connectionRepository.saveAndFlush(connection(projectId, "123").withCredential("second-token"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `deleting a connection removes its owned credential`() {
        val connection = connection(UUID.randomUUID(), "123").withCredential("delete-me-token")
        val saved = connectionRepository.saveAndFlush(connection)

        connectionRepository.delete(saved)
        connectionRepository.flush()

        val credentialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM confluence_credentials WHERE connection_id = ?",
            Long::class.java,
            saved.id,
        )
        assertThat(credentialCount).isZero()
    }

    private fun connection(
        projectId: UUID,
        spaceId: String,
        allowlist: List<String> = emptyList(),
        denylist: List<String> = emptyList(),
    ): ConfluenceSpaceConnection {
        return ConfluenceSpaceConnection(
            projectId = projectId,
            baseUrl = "https://tenant.atlassian.net",
            spaceId = spaceId,
            spaceKey = "ENG",
            pageAllowlistInternal = allowlist.toMutableList(),
            pageDenylistInternal = denylist.toMutableList(),
        )
    }

    private fun ConfluenceSpaceConnection.withCredential(token: String): ConfluenceSpaceConnection {
        configureCredential("fake-user@example.invalid", token)
        return this
    }
}
