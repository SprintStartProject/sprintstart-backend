package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionResult
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionConnectionRuntimeService
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionPageIngestionService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.overview.models.IProjectScopedSourcePatcher
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.SourcePatchValidationException
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import org.springframework.stereotype.Component
import java.util.UUID

/** Registers Notion in connector overview and delegates project-scoped page ingestion. */
@Component
internal class NotionConnector(
    private val connectionService: NotionConnectionRuntimeService,
    private val ingestionService: NotionPageIngestionService,
) : IConnector,
    IProjectScopedSourcePatcher {
    override val id: String = "notion"
    override val displayName: String = "Notion Cloud Connector"
    val sourceSystem: SourceSystem = SourceSystem.NOTION

    override fun getSources(): List<ConnectorSource> {
        return emptyList()
    }

    override fun getSources(projectId: UUID): List<ConnectorSource> {
        return connectionService.getSourceInstances(projectId).map { source ->
            ConnectorSource(
                id = source.connectionId.toString(),
                name = source.pageTitle,
                url = source.pageUrl,
                enabled = source.enabled,
            )
        }
    }

    override fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        throw SourcePatchValidationException("projectId is required for Notion source updates")
    }

    override fun patchSources(
        projectId: UUID,
        requestedSources: Map<String, Boolean>,
    ): List<ConnectorSource> {
        val requestedStatuses = requestedSources.mapKeys { (sourceId, _) ->
            runCatching { UUID.fromString(sourceId) }.getOrElse {
                throw NotionPageConnectionConfigurationException("Notion connection source ID is invalid")
            }
        }
        if (requestedStatuses.size != requestedSources.size) {
            throw NotionPageConnectionConfigurationException("Notion connection source IDs must be unique")
        }
        return connectionService.patchSources(projectId, requestedStatuses).map { source ->
            ConnectorSource(
                id = source.connectionId.toString(),
                name = source.pageTitle,
                url = source.pageUrl,
                enabled = source.enabled,
            )
        }
    }

    suspend fun ingest(projectId: UUID, connectionId: UUID): NotionIngestionResult {
        return ingestionService.ingest(projectId, connectionId)
    }
}
