package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionRuntimeService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionSourceSnapshot
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluencePageIngestionService
import com.sprintstart.sprintstartbackend.connectors.confluence.service.safePageUrl
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.overview.models.IProjectScopedSourcePatcher
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.SourcePatchValidationException
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import org.springframework.stereotype.Component
import java.util.UUID

/** Registers Confluence in connector overview and delegates project-scoped page ingestion. */
@Component
internal class ConfluenceConnector(
    private val connectionService: ConfluenceConnectionRuntimeService,
    private val ingestionService: ConfluencePageIngestionService,
) : IConnector,
    IProjectScopedSourcePatcher {
    override val id: String = "confluence"
    override val displayName: String = "Confluence Cloud Connector"
    val sourceSystem: SourceSystem = SourceSystem.CONFLUENCE

    override fun getSources(): List<ConnectorSource> {
        throw SourcePatchValidationException("projectId is required for Confluence source discovery")
    }

    override fun getSources(projectId: UUID): List<ConnectorSource> {
        return connectionService.getSourceConnections(projectId).map { source -> source.toConnectorSource() }
    }

    override fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        throw SourcePatchValidationException("projectId is required for Confluence source updates")
    }

    override fun patchSources(
        projectId: UUID,
        requestedSources: Map<String, Boolean>,
    ): List<ConnectorSource> {
        val requestedStatuses = requestedSources.mapKeys { (sourceId, _) ->
            runCatching { UUID.fromString(sourceId) }.getOrElse {
                throw ConfluenceConnectionConfigurationException("Confluence connection source ID is invalid")
            }
        }
        if (requestedStatuses.size != requestedSources.size) {
            throw ConfluenceConnectionConfigurationException("Confluence connection source IDs must be unique")
        }
        return connectionService.patchSources(projectId, requestedStatuses).map { source ->
            source.toConnectorSource()
        }
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
