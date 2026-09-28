package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Commits one validated Confluence connection in a single transaction.
 *
 * This lives in its own bean on purpose. [ConfluenceConnectionService.createConnection] suspends while it
 * validates the space against Confluence, so it cannot own the transaction itself: the coroutine resumes on
 * another thread, where Spring's thread-bound transaction synchronization no longer exists. A private method of
 * the same bean would not help either, because self-invocation bypasses the transactional proxy -- the same
 * reason the GitHub connector routes its per-repository transactions through an orchestrator bean. Going through
 * a second bean keeps the duplicate check and the insert in one real transaction, and guarantees that the
 * connection is committed before its caller launches the initial ingestion.
 */
@Service
internal class ConfluenceConnectionPersistenceService(
    private val connectionRepository: ConfluenceSpaceConnectionRepository,
) {
    /**
     * Stores one already validated connection.
     *
     * The duplicate check is repeated here even though the caller rejects known duplicates before contacting
     * Confluence: only this check shares a transaction with the insert.
     */
    @Transactional
    fun persist(connection: ConfluenceSpaceConnection): ConfluenceConnectionResponse {
        rejectDuplicate(connection)
        val saved = try {
            connectionRepository.saveAndFlush(connection)
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw ConfluenceConnectionAlreadyExistsException(connection.projectId, connection.spaceId)
        }
        return saved.toResponse()
    }

    private fun rejectDuplicate(connection: ConfluenceSpaceConnection) {
        val exists = connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(
            connection.projectId,
            connection.baseUrl,
            connection.spaceId,
        )
        if (exists) {
            throw ConfluenceConnectionAlreadyExistsException(connection.projectId, connection.spaceId)
        }
    }
}
