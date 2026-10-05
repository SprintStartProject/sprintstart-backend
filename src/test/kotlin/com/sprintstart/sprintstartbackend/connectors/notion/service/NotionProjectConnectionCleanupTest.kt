package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class, NotionWorkspaceConnectionPersistenceService::class)
class NotionProjectConnectionCleanupTest {
    @Autowired
    private lateinit var repository: NotionWorkspaceConnectionRepository

    @Autowired
    private lateinit var persistenceService: NotionWorkspaceConnectionPersistenceService

    @Test
    fun `project deletion removes only its Notion connections`() {
        val deletedProject = UUID.randomUUID()
        val keptProject = UUID.randomUUID()
        val deletedConnection = repository.save(connection(deletedProject, "deleted-page"))
        val keptConnection = repository.save(connection(keptProject, "kept-page"))
        repository.flush()

        persistenceService.deleteAllForProject(deletedProject)

        assertThat(repository.existsById(deletedConnection.id)).isFalse()
        assertThat(repository.existsById(keptConnection.id)).isTrue()
    }

    private fun connection(projectId: UUID, pageId: String): NotionWorkspaceConnection {
        return NotionWorkspaceConnection(
            projectId = projectId,
            workspaceId = pageId,
            workspaceName = pageId,
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
    }
}
