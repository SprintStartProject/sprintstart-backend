package com.sprintstart.sprintstartbackend.connectors.confluence.repository

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
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
    private lateinit var entityManager: EntityManager

    @Test
    fun `connection round-trips its credential reference and project ownership`() {
        val projectId = UUID.randomUUID()
        val connection = connection(projectId, "123", listOf("10", "20"), listOf("20"))

        val saved = connectionRepository.saveAndFlush(connection)
        entityManager.clear()

        val loaded = connectionRepository.findByIdAndProjectId(saved.id, projectId)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.projectId).isEqualTo(projectId)
        assertThat(loaded.baseUrl).isEqualTo("https://tenant.atlassian.net")
        assertThat(loaded.spaceId).isEqualTo("123")
        assertThat(loaded.spaceKey).isEqualTo("ENG")
        assertThat(loaded.pageAllowlist).containsExactly("10", "20")
        assertThat(loaded.pageDenylist).containsExactly("20")
        assertThat(loaded.credentialAuthId).isEqualTo("auth-id")
        assertThat(loaded.credentialName).isEqualTo("team-token")
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
        connectionRepository.saveAndFlush(connection(projectId, "123"))

        assertThatThrownBy {
            connectionRepository.saveAndFlush(connection(projectId, "123"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
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
            credentialAuthId = "auth-id",
            credentialName = "team-token",
            pageAllowlistInternal = allowlist.toMutableList(),
            pageDenylistInternal = denylist.toMutableList(),
        )
    }
}
