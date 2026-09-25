package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionPageConnectionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    fun delete(projectId: UUID, connectionId: UUID) {
        val connection = connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw NotionPageConnectionNotFoundException(connectionId, projectId)
        connectionRepository.delete(connection)
        connectionRepository.flush()
    }
}
