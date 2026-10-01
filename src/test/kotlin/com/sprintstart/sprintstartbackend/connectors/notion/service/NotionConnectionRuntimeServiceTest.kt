package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionPageConnectionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class NotionConnectionRuntimeServiceTest {
    private val repository = mockk<NotionPageConnectionRepository>()
    private val service = NotionConnectionRuntimeService(repository)

    @Test
    fun `returns credential-free source instances in repository order`() {
        val projectId = UUID.randomUUID()
        val connection = connection(projectId = projectId)
        every { repository.findAllByProjectIdOrderByCreatedAtAsc(projectId) } returns listOf(connection)

        val result = service.getSourceInstances(projectId).single()

        assertThat(result.connectionId).isEqualTo(connection.id)
        assertThat(result.pageTitle).isEqualTo("Engineering Runbook")
        assertThat(result.sourceRef).isEqualTo(connection.pageUrl)
        assertThat(result.enabled).isTrue()
    }

    @Test
    fun `batch patch validates every project connection before changing any status`() {
        val projectId = UUID.randomUUID()
        val existing = connection(projectId = projectId)
        val missingId = UUID.randomUUID()
        every {
            repository.findAllByIdInAndProjectId(listOf(existing.id, missingId), projectId)
        } returns listOf(existing)

        assertFailsWith<NotionPageConnectionNotFoundException> {
            service.patchSources(projectId, linkedMapOf(existing.id to false, missingId to true))
        }
        assertThat(existing.sourceEnabled).isTrue()
    }

    private fun connection(projectId: UUID): NotionPageConnection {
        return NotionPageConnection(
            projectId = projectId,
            pageId = "page-1",
            pageTitle = "Engineering Runbook",
            pageUrl = "https://www.notion.so/page-1",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
    }
}
