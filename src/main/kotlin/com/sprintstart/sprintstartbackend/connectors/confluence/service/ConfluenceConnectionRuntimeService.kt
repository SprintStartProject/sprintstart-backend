package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Provides runtime connection views for connector overview and ingestion. */
@Service
internal class ConfluenceConnectionRuntimeService(
    private val connectionRepository: ConfluenceSpaceConnectionRepository,
) {
    @Transactional(readOnly = true)
    fun getSourceConnections(projectId: UUID? = null): List<ConfluenceConnectionSourceSnapshot> {
        val connections = if (projectId == null) {
            connectionRepository.findAllByOrderByCreatedAtAsc()
        } else {
            connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId)
        }
        return connections.map { connection ->
            ConfluenceConnectionSourceSnapshot(
                id = connection.id,
                baseUrl = connection.baseUrl,
                spaceId = connection.spaceId,
                spaceKey = connection.spaceKey,
                sourceEnabled = connection.sourceEnabled,
            )
        }
    }

    @Transactional
    fun patchSource(connectionId: UUID, sourceEnabled: Boolean) {
        val connection = connectionRepository.findById(connectionId).orElseThrow {
            ConfluenceConnectionConfigurationException("Confluence connection $connectionId was not found", 404)
        }
        connection.sourceEnabled = sourceEnabled
    }

    /** Loads one project-scoped connection and decrypts its credential for trusted ingestion code. */
    @Transactional(readOnly = true)
    fun getConnectionForIngestion(projectId: UUID, connectionId: UUID): ConfluenceConnectionIngestionSnapshot {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw ConfluenceConnectionNotFoundException(connectionId, projectId)
        return ConfluenceConnectionIngestionSnapshot(
            id = connection.id,
            projectId = connection.projectId,
            baseUrl = connection.baseUrl,
            spaceId = connection.spaceId,
            spaceKey = connection.spaceKey,
            sourceEnabled = connection.sourceEnabled,
            pageAllowlist = connection.pageAllowlist,
            pageDenylist = connection.pageDenylist,
            credentials = ConfluenceClientCredentials(connection.credential.email, connection.credential.apiToken),
        )
    }
}
