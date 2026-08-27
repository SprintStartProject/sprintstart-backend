package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionStatus
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionRuntimeService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionSourceSnapshot
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

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
    fun `patch delegates by stable connection ID`() {
        val connectionId = UUID.randomUUID()
        every { connectionService.patchSource(connectionId, false) } returns Unit

        connector.patchSource(ConnectorSource(connectionId.toString(), "ENG", "safe", true), false)

        verify { connectionService.patchSource(connectionId, false) }
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
}
