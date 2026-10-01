package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.external.NotionConnectionApi
import com.sprintstart.sprintstartbackend.connectors.notion.external.NotionSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionPageConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Provides safe runtime views and project-scoped status updates for Notion connections. */
@Service
internal class NotionConnectionRuntimeService(
    private val connectionRepository: NotionPageConnectionRepository,
) : NotionConnectionApi {
    @Transactional(readOnly = true)
    override fun getConnectionIdsByProject(projectId: UUID): List<UUID> {
        return connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId).map { connection -> connection.id }
    }

    @Transactional(readOnly = true)
    override fun getSourceInstances(projectId: UUID?): List<NotionSourceInstanceDto> {
        val connections = if (projectId == null) {
            connectionRepository.findAll().sortedBy { connection -> connection.createdAt }
        } else {
            connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId)
        }
        return connections.map { connection -> connection.toSourceInstanceDto() }
    }

    /** Atomically patches validated source statuses and preserves request order. */
    @Transactional
    fun patchSources(
        projectId: UUID,
        requestedStatuses: Map<UUID, Boolean>,
    ): List<NotionSourceInstanceDto> {
        val connections = connectionRepository.findAllByIdInAndProjectId(requestedStatuses.keys, projectId)
        val connectionsById = connections.associateBy { connection -> connection.id }
        requestedStatuses.keys.firstOrNull { connectionId -> connectionId !in connectionsById }?.let { missingId ->
            throw NotionPageConnectionNotFoundException(missingId, projectId)
        }
        return requestedStatuses.map { (connectionId, enabled) ->
            val connection = requireNotNull(connectionsById[connectionId])
            connection.sourceEnabled = enabled
            connection.toSourceInstanceDto()
        }
    }

    private fun NotionPageConnection.toSourceInstanceDto(): NotionSourceInstanceDto {
        return NotionSourceInstanceDto(
            connectionId = id,
            sourceRef = pageUrl,
            pageId = pageId,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            status = if (sourceEnabled) "CONNECTED" else "DISABLED",
            enabled = sourceEnabled,
            lastSyncedAt = lastSyncedAt,
        )
    }
}
