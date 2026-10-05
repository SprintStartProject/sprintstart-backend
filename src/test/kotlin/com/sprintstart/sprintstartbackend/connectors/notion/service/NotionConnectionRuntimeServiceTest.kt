package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class NotionConnectionRuntimeServiceTest {
    private val repository = mockk<NotionWorkspaceConnectionRepository>()
    private val service = NotionConnectionRuntimeService(repository)

    @Test
    fun `returns credential-free source instances in repository order`() {
        val projectId = UUID.randomUUID()
        val connection = connection(projectId = projectId)
        every { repository.findAllByProjectIdOrderByCreatedAtAsc(projectId) } returns listOf(connection)

        val result = service.getSourceInstances(projectId).single()

        assertThat(result.connectionId).isEqualTo(connection.id)
        assertThat(result.workspaceName).isEqualTo("Engineering Runbook")
        assertThat(result.sourceRef).isEqualTo(connection.workspaceId)
        assertThat(result.enabled).isTrue()
    }

    @Test
    fun `batch patch validates every project connection before changing any status`() {
        val projectId = UUID.randomUUID()
        val existing = connection(projectId = projectId)
        val missingId = UUID.randomUUID()
        val requested = linkedMapOf(existing.id to false, missingId to true)
        every {
            repository.findAllByIdInAndProjectId(requested.keys, projectId)
        } returns listOf(existing)

        assertFailsWith<NotionWorkspaceConnectionNotFoundException> {
            service.patchSources(projectId, requested)
        }
        assertThat(existing.sourceEnabled).isTrue()
    }

    private fun connection(projectId: UUID): NotionWorkspaceConnection {
        return NotionWorkspaceConnection(
            projectId = projectId,
            workspaceId = "page-1",
            workspaceName = "Engineering Runbook",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
    }
}
