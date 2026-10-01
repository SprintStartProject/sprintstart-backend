package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionPageConnectionRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
internal class NotionPageConnectionPersistenceService(
    private val connectionRepository: NotionPageConnectionRepository,
) {
    @Transactional
    fun persist(connection: NotionPageConnection): NotionPageConnectionResponse {
        if (connectionRepository.existsByProjectIdAndPageId(connection.projectId, connection.pageId)) {
            throw NotionPageConnectionAlreadyExistsException(connection.projectId, connection.pageId)
        }
        val savedConnection = try {
            connectionRepository.saveAndFlush(connection)
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw NotionPageConnectionAlreadyExistsException(connection.projectId, connection.pageId)
        }
        return savedConnection.toResponse()
    }

    @Transactional(readOnly = true)
    fun findAll(projectId: UUID): List<NotionPageConnectionResponse> {
        return connectionRepository
            .findAllByProjectIdOrderByCreatedAtAsc(projectId)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun requireConnection(projectId: UUID, connectionId: UUID): NotionPageConnection {
        return connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionPageConnectionNotFoundException(connectionId, projectId)
    }

    @Transactional
    fun recordSuccessfulSync(
        projectId: UUID,
        connectionId: UUID,
        pageTitle: String,
        pageUrl: String,
        lastEditedTime: Instant,
        contentHash: String,
    ) {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionPageConnectionNotFoundException(connectionId, projectId)
        connection.pageTitle = pageTitle
        connection.pageUrl = pageUrl
        connection.lastEditedTime = lastEditedTime
        connection.contentHash = contentHash
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
    ): NotionPageConnectionResponse {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionPageConnectionNotFoundException(connectionId, projectId)
        connection.scheduleSpec = scheduleSpec
        connection.schedule = schedule
        connection.autoUpdate = autoUpdate
        connection.nextSyncAt = nextSyncAt
        return connectionRepository.saveAndFlush(connection).toResponse()
    }

    @Transactional
    fun delete(projectId: UUID, connectionId: UUID) {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionPageConnectionNotFoundException(connectionId, projectId)
        connectionRepository.delete(connection)
        connectionRepository.flush()
    }
}
