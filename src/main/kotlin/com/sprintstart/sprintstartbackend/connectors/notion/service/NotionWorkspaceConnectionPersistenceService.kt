package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionWorkspaceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionSyncedPageRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Owns transactional writes for project-scoped Notion workspace connections.
 *
 * Duplicate credential/workspace scopes are rejected per project. Destructive operations lock the
 * connection and remove its per-page state so a concurrent sync cannot recreate deleted state.
 */
@Service
internal class NotionWorkspaceConnectionPersistenceService(
    private val connectionRepository: NotionWorkspaceConnectionRepository,
    private val syncedPageRepository: NotionSyncedPageRepository,
) {
    /** Persists a unique project/credential scope and translates database races into a domain conflict. */
    @Transactional
    fun persist(connection: NotionWorkspaceConnection): NotionWorkspaceConnectionResponse {
        if (connectionRepository.existsByProjectIdAndCredentialAuthIdAndCredentialName(
                connection.projectId,
                connection.credentialAuthId,
                connection.credentialName,
            ) ||
            connection.workspaceId?.let { workspaceId ->
                connection.tokenOwnerId?.let { ownerId ->
                    connectionRepository.existsByProjectIdAndWorkspaceIdAndTokenOwnerId(
                        connection.projectId,
                        workspaceId,
                        ownerId,
                    )
                }
            } == true
        ) {
            throw NotionWorkspaceConnectionAlreadyExistsException(connection.projectId, connection.workspaceName)
        }
        val savedConnection = try {
            connectionRepository.saveAndFlush(connection)
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw NotionWorkspaceConnectionAlreadyExistsException(connection.projectId, connection.workspaceName)
        }
        return savedConnection.toResponse()
    }

    @Transactional(readOnly = true)
    fun findAll(projectId: UUID): List<NotionWorkspaceConnectionResponse> {
        return connectionRepository
            .findAllByProjectIdOrderByCreatedAtAsc(projectId)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun requireConnection(projectId: UUID, connectionId: UUID): NotionWorkspaceConnection {
        return connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionWorkspaceConnectionNotFoundException(connectionId, projectId)
    }

    @Transactional
    fun recordSuccessfulSync(
        projectId: UUID,
        connectionId: UUID,
    ) {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionWorkspaceConnectionNotFoundException(connectionId, projectId)
        connection.lastSyncedAt = Instant.now()
        connectionRepository.saveAndFlush(connection)
    }

    @Transactional
    fun configureSchedule(
        projectId: UUID,
        connectionId: UUID,
        scheduleSpec: ScheduleSpec,
        schedule: String,
        autoUpdate: Boolean,
        nextSyncAt: Instant?,
    ): NotionWorkspaceConnectionResponse {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionWorkspaceConnectionNotFoundException(connectionId, projectId)
        connection.scheduleSpec = scheduleSpec
        connection.schedule = schedule
        connection.autoUpdate = autoUpdate
        connection.nextSyncAt = nextSyncAt
        return connectionRepository.saveAndFlush(connection).toResponse()
    }

    /** Deletes one locked connection and all state whose lifecycle belongs to it. */
    @Transactional
    fun delete(projectId: UUID, connectionId: UUID) {
        val connection = connectionRepository.findForUpdate(connectionId, projectId)
            ?: throw NotionWorkspaceConnectionNotFoundException(connectionId, projectId)
        syncedPageRepository.deleteAllByConnectionId(connectionId)
        connectionRepository.delete(connection)
        connectionRepository.flush()
    }

    /** Deletes every connection and page state before the owning project transaction commits. */
    @Transactional
    fun deleteAllForProject(projectId: UUID) {
        connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId).forEach {
            connectionRepository.findForUpdate(it.id, projectId)
            syncedPageRepository.deleteAllByConnectionId(it.id)
        }
        connectionRepository.deleteAllByProjectId(projectId)
        connectionRepository.flush()
    }
}
