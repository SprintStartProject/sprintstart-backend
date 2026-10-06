package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.external.NotionSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionOutcome
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionResult
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionConnectionRuntimeService
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionWorkspaceIngestionService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.SourcePatchValidationException
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class NotionConnectorTest {
    private val connectionService = mockk<NotionConnectionRuntimeService>()
    private val ingestionService = mockk<NotionWorkspaceIngestionService>()
    private val connector = NotionConnector(connectionService, ingestionService)

    @Test
    fun `identifies and maps Notion workspace sources`() {
        val projectId = UUID.randomUUID()
        val source = sourceInstance()
        every { connectionService.getSourceInstances(projectId) } returns listOf(source)

        val result = connector.getSources(projectId)

        assertThat(connector.id).isEqualTo("notion")
        assertThat(connector.displayName).isEqualTo("Notion Cloud Connector")
        assertThat(connector.sourceSystem).isEqualTo(SourceSystem.NOTION)
        assertThat(result).containsExactly(
            ConnectorSource(
                id = source.connectionId.toString(),
                name = "Engineering Runbook",
                url = "https://www.notion.so/page-1",
                enabled = true,
            ),
        )
    }

    @Test
    fun `unscoped discovery is empty while unscoped patching fails closed`() {
        assertThat(connector.getSources()).isEmpty()
        assertFailsWith<SourcePatchValidationException> {
            connector.patchSource(ConnectorSource(UUID.randomUUID().toString(), "Page", "safe", true), false)
        }
        verify(exactly = 0) { connectionService.getSourceInstances(any()) }
        verify(exactly = 0) { connectionService.patchSources(any(), any()) }
    }

    @Test
    fun `project scoped patch delegates normalized ids and preserves order`() {
        val projectId = UUID.randomUUID()
        val first = sourceInstance(UUID.randomUUID(), "First", true)
        val second = sourceInstance(UUID.randomUUID(), "Second", false)
        val requested = linkedMapOf(second.connectionId.toString() to false, first.connectionId.toString() to true)
        every {
            connectionService.patchSources(
                projectId,
                linkedMapOf(second.connectionId to false, first.connectionId to true),
            )
        } returns listOf(second, first)

        val result = connector.patchSources(projectId, requested)

        assertThat(result.map { source -> source.id })
            .containsExactly(second.connectionId.toString(), first.connectionId.toString())
        assertThat(result.map { source -> source.enabled }).containsExactly(false, true)
    }

    @Test
    fun `ingestion delegates project and connection scope`() = runTest {
        val projectId = UUID.randomUUID()
        val connectionId = UUID.randomUUID()
        val expected = NotionIngestionResult(UUID.randomUUID(), connectionId, NotionIngestionOutcome.CREATED)
        coEvery { ingestionService.ingest(projectId, connectionId, true) } returns expected

        assertThat(connector.ingest(projectId, connectionId)).isEqualTo(expected)

        coVerify(exactly = 1) { ingestionService.ingest(projectId, connectionId, true) }
    }

    private fun sourceInstance(
        connectionId: UUID = UUID.randomUUID(),
        title: String = "Engineering Runbook",
        enabled: Boolean = true,
    ): NotionSourceInstanceDto {
        return NotionSourceInstanceDto(
            connectionId = connectionId,
            sourceRef = "https://www.notion.so/page-1",
            workspaceId = "page-1",
            workspaceName = title,
            workspaceUrl = "https://www.notion.so/page-1",
            status = if (enabled) "CONNECTED" else "DISABLED",
            enabled = enabled,
            lastSyncedAt = null,
        )
    }
}
