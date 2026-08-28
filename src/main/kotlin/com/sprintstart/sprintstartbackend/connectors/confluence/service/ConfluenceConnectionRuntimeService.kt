package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
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
    fun getSourceConnections(projectId: UUID): List<ConfluenceConnectionSourceSnapshot> {
        return connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId).map { connection ->
            ConfluenceConnectionSourceSnapshot(
                id = connection.id,
                baseUrl = connection.baseUrl,
                spaceId = connection.spaceId,
                spaceKey = connection.spaceKey,
                sourceEnabled = connection.sourceEnabled,
            )
        }
    }

    /** Atomically patches a validated batch of project-owned connection statuses. */
    @Transactional
    fun patchSources(
        projectId: UUID,
        requestedStatuses: Map<UUID, Boolean>,
    ): List<ConfluenceConnectionSourceSnapshot> {
        val connections = connectionRepository.findAllByIdInAndProjectId(requestedStatuses.keys, projectId)
        val connectionsById = connections.associateBy { connection -> connection.id }
        requestedStatuses.keys.firstOrNull { connectionId -> connectionId !in connectionsById }?.let { missingId ->
            throw ConfluenceConnectionNotFoundException(missingId, projectId)
        }
        return requestedStatuses.map { (connectionId, enabled) ->
            val connection = requireNotNull(connectionsById[connectionId])
            connection.sourceEnabled = enabled
            connection.toSourceSnapshot()
        }
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

    private fun ConfluenceSpaceConnection.toSourceSnapshot(): ConfluenceConnectionSourceSnapshot {
        return ConfluenceConnectionSourceSnapshot(
            id = id,
            baseUrl = baseUrl,
            spaceId = spaceId,
            spaceKey = spaceKey,
            sourceEnabled = sourceEnabled,
        )
    }
}
