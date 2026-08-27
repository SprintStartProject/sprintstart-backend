package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionRuntimeService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionSourceSnapshot
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.safePageUrl
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import org.springframework.stereotype.Component
import java.util.UUID

/** Registers Confluence in connector overview and delegates project-scoped page ingestion. */
@Component
internal class ConfluenceConnector(
    private val connectionService: ConfluenceConnectionRuntimeService,
    private val ingestionService: ConfluencePageIngestionService,
) : IConnector {
    override val id: String = "confluence"
    override val displayName: String = "Confluence Cloud Connector"
    val sourceSystem: SourceSystem = SourceSystem.CONFLUENCE

    override fun getSources(): List<ConnectorSource> {
        return connectionService.getSourceConnections().map { source -> source.toConnectorSource() }
    }

    override fun getSources(projectId: UUID): List<ConnectorSource> {
        return connectionService.getSourceConnections(projectId).map { source -> source.toConnectorSource() }
    }

    override fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        val connectionId = runCatching { UUID.fromString(source.id) }.getOrElse {
            throw ConfluenceConnectionConfigurationException("Confluence connection source ID is invalid")
        }
        connectionService.patchSource(connectionId, newStatus)
    }

    suspend fun ingest(projectId: UUID, connectionId: UUID): ConfluenceIngestionResult {
        return ingestionService.ingest(projectId, connectionId)
    }

    private fun ConfluenceConnectionSourceSnapshot.toConnectorSource(): ConnectorSource {
        return ConnectorSource(
            id = id.toString(),
            name = spaceKey,
            url = safePageUrl(baseUrl, "/wiki/spaces/$spaceKey") ?: baseUrl,
            enabled = sourceEnabled,
        )
    }
}
