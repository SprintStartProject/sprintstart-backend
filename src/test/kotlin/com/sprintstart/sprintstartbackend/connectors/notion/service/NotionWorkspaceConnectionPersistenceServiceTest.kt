package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionSyncedPageRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

internal class NotionWorkspaceConnectionPersistenceServiceTest {
    private val connectionRepository = mockk<NotionWorkspaceConnectionRepository>()
    private val syncedPageRepository = mockk<NotionSyncedPageRepository>()
    private val service = NotionWorkspaceConnectionPersistenceService(connectionRepository, syncedPageRepository)
    private val projectId = UUID.randomUUID()

    @Test
    fun `a connection that loses the unique constraint race is reported as a duplicate with the cause`() {
        val connection = NotionWorkspaceConnection(
            projectId = projectId,
            workspaceName = "Docs",
            credentialAuthId = "auth",
            credentialName = "team",
        )
        val conflict = DataIntegrityViolationException("duplicate key")
        every {
            connectionRepository.existsByProjectIdAndCredentialAuthIdAndCredentialName(projectId, "auth", "team")
        } returns false
        every { connectionRepository.saveAndFlush(connection) } throws conflict

        val thrown = runCatching { service.persist(connection) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(NotionWorkspaceConnectionAlreadyExistsException::class.java)
        assertThat(thrown?.cause).isSameAs(conflict)
    }
}
