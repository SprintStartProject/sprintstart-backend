package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class ConfluenceConnectionRuntimeServiceTest {
    private val repository = mockk<ConfluenceSpaceConnectionRepository>()
    private val service = ConfluenceConnectionRuntimeService(repository)

    @Test
    fun `module API returns safe project scoped source instances and connection ids`() {
        val projectId = UUID.randomUUID()
        val connection = connection(projectId, "ENG").also { stored ->
            stored.createdAt = Instant.parse("2026-08-28T10:00:00Z")
        }
        every { repository.findAllByProjectIdOrderByCreatedAtAsc(projectId) } returns listOf(connection)

        val ids = service.getConnectionIdsByProject(projectId)
        val source = service.getSourceInstances(projectId).single()

        assertThat(ids).containsExactly(connection.id)
        assertThat(source.connectionId).isEqualTo(connection.id)
        assertThat(source.sourceRef).isEqualTo("https://tenant.invalid|${connection.spaceId}")
        assertThat(source.spaceKey).isEqualTo("ENG")
        assertThat(source.sourceUrl).isEqualTo("https://tenant.invalid/wiki/spaces/ENG")
        assertThat(source.status).isEqualTo("CONNECTED")
        assertThat(source.enabled).isTrue()
        assertThat(source.toString()).doesNotContain("token", "credential", "Authorization")
        verify(exactly = 2) { repository.findAllByProjectIdOrderByCreatedAtAsc(projectId) }
    }

    @Test
    fun `batch patch loads once updates atomically and preserves request order`() {
        val projectId = UUID.randomUUID()
        val first = connection(projectId, "ONE")
        val second = connection(projectId, "TWO")
        val requested = linkedMapOf(second.id to false, first.id to true)
        every { repository.findAllByIdInAndProjectId(requested.keys, projectId) } returns listOf(first, second)

        val result = service.patchSources(projectId, requested)

        assertThat(result.map { source -> source.id }).containsExactly(second.id, first.id)
        assertThat(result.map { source -> source.sourceEnabled }).containsExactly(false, true)
        assertThat(second.sourceEnabled).isFalse()
        assertThat(first.sourceEnabled).isTrue()
        verify(exactly = 1) { repository.findAllByIdInAndProjectId(requested.keys, projectId) }
        verify(exactly = 0) { repository.findById(any()) }
    }

    @Test
    fun `missing or foreign project source rejects whole batch before mutation`() {
        val projectId = UUID.randomUUID()
        val owned = connection(projectId, "ONE").also { connection -> connection.sourceEnabled = true }
        val foreignId = UUID.randomUUID()
        val requested = linkedMapOf(owned.id to false, foreignId to false)
        every { repository.findAllByIdInAndProjectId(requested.keys, projectId) } returns listOf(owned)

        assertThatThrownBy { service.patchSources(projectId, requested) }
            .isInstanceOf(ConfluenceConnectionNotFoundException::class.java)

        assertThat(owned.sourceEnabled).isTrue()
    }

    private fun connection(projectId: UUID, spaceKey: String): ConfluenceSpaceConnection {
        val spaceId = UUID
            .randomUUID()
            .mostSignificantBits
            .toString()
            .removePrefix("-")
        return ConfluenceSpaceConnection(
            projectId = projectId,
            baseUrl = "https://tenant.invalid",
            spaceId = spaceId,
            spaceKey = spaceKey,
        )
    }
}
