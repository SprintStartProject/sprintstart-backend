package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionStatus
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionRuntimeService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionSourceSnapshot
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.SourcePatchValidationException
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class ConfluenceConnectorTest {
    private val connectionService = mockk<ConfluenceConnectionRuntimeService>()
    private val ingestionService = mockk<ConfluencePageIngestionService>()
    private val connector = ConfluenceConnector(connectionService, ingestionService)

    @Test
    fun `identifies and maps Confluence sources`() {
        val projectId = UUID.randomUUID()
        val source = ConfluenceConnectionSourceSnapshot(
            id = UUID.randomUUID(),
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "42",
            spaceKey = "ENG",
            sourceEnabled = true,
        )
        every { connectionService.getSourceConnections(projectId) } returns listOf(source)

        val result = connector.getSources(projectId)

        assertThat(connector.id).isEqualTo("confluence")
        assertThat(connector.sourceSystem).isEqualTo(SourceSystem.CONFLUENCE)
        assertThat(result.single()).isEqualTo(
            ConnectorSource(
                id = source.id.toString(),
                name = "ENG",
                url = "https://tenant.atlassian.net/wiki/spaces/ENG",
                enabled = true,
            ),
        )
    }

    @Test
    fun `unscoped discovery is empty while unscoped updates fail closed`() {
        assertThat(connector.getSources()).isEmpty()
        assertFailsWith<SourcePatchValidationException> {
            connector.patchSource(ConnectorSource(UUID.randomUUID().toString(), "ENG", "safe", true), false)
        }
        verify(exactly = 0) { connectionService.getSourceConnections(any()) }
        verify(exactly = 0) { connectionService.patchSources(any(), any()) }
    }

    @Test
    fun `project scoped batch patch delegates normalized ids and preserves order`() {
        val projectId = UUID.randomUUID()
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val requested = linkedMapOf(secondId.toString() to false, firstId.toString() to true)
        every {
            connectionService.patchSources(projectId, linkedMapOf(secondId to false, firstId to true))
        } returns listOf(
            sourceSnapshot(secondId, "TWO", false),
            sourceSnapshot(firstId, "ONE", true),
        )

        val result = connector.patchSources(projectId, requested)

        assertThat(result.map { source -> source.id }).containsExactly(secondId.toString(), firstId.toString())
        assertThat(result.map { source -> source.enabled }).containsExactly(false, true)
    }

    @Test
    fun `ingestion delegates project and connection scope`() = runTest {
        val projectId = UUID.randomUUID()
        val connectionId = UUID.randomUUID()
        val expected = ConfluenceIngestionResult(
            UUID.randomUUID(),
            connectionId,
            1,
            1,
            0,
            1,
            0,
            0,
            0,
            emptyList(),
            ConfluenceIngestionStatus.COMPLETED,
        )
        coEvery { ingestionService.ingest(projectId, connectionId) } returns expected

        assertThat(connector.ingest(projectId, connectionId)).isEqualTo(expected)
    }

    private fun sourceSnapshot(id: UUID, key: String, enabled: Boolean) = ConfluenceConnectionSourceSnapshot(
        id = id,
        baseUrl = "https://tenant.atlassian.net",
        spaceId = "42",
        spaceKey = key,
        sourceEnabled = enabled,
    )
}
