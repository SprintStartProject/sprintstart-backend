package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceExternalServiceException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceResourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceCredentialRepository
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Validates and persists project-scoped Confluence Cloud space connections. */
@Service
internal class ConfluenceConnectionService(
    private val confluenceClient: ConfluenceClient,
    private val connectionRepository: ConfluenceSpaceConnectionRepository,
    private val credentialRepository: ConfluenceCredentialRepository,
    private val userApi: UserApi,
) {
    /**
     * Validates the selected remote space before atomically storing its connection and credential.
     *
     * The canonical space ID and key come from Confluence. Nothing is persisted when validation fails.
     */
    @Transactional
    suspend fun createConnection(
        authId: String,
        projectId: UUID,
        request: CreateConfluenceConnectionRequest,
    ): ConfluenceConnectionResponse {
        requireProjectAccess(authId, projectId)
        val normalizedBaseUrl = normalizeConfluenceBaseUrl(request.baseUrl)
        val requestedSpaceId = normalizeSpaceId(request.spaceId)
        val allowlist = normalizeConfluencePageIds(request.pageAllowlist, "page allowlist")
        val denylist = normalizeConfluencePageIds(request.pageDenylist, "page denylist")
        val credentials = validatedCredentials(request.email, request.apiToken)

        rejectDuplicate(projectId, normalizedBaseUrl, requestedSpaceId)
        val space = retrieveSpace(normalizedBaseUrl, credentials, requestedSpaceId)
        val canonicalSpaceId = normalizeSpaceId(space.id)
        if (canonicalSpaceId != requestedSpaceId || space.key.isBlank()) {
            throw ConfluenceConnectionConfigurationException(
                "Confluence returned inconsistent space identity metadata",
                httpStatus = 502,
            )
        }

        val connection = ConfluenceSpaceConnection(
            projectId = projectId,
            baseUrl = normalizedBaseUrl,
            spaceId = canonicalSpaceId,
            spaceKey = space.key.trim(),
            pageAllowlistInternal = allowlist.toMutableList(),
            pageDenylistInternal = denylist.toMutableList(),
        )
        connection.configureCredential(credentials.email.trim(), credentials.apiToken.trim())

        return try {
            connectionRepository.saveAndFlush(connection).toResponse()
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw ConfluenceConnectionAlreadyExistsException(projectId, canonicalSpaceId)
        }
    }

    @Transactional(readOnly = true)
    fun getConnections(authId: String, projectId: UUID): List<ConfluenceConnectionResponse> {
        requireProjectAccess(authId, projectId)
        return connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId).map { connection ->
            connection.toResponse()
        }
    }

    @Transactional(readOnly = true)
    fun getConnection(authId: String, projectId: UUID, connectionId: UUID): ConfluenceConnectionResponse {
        requireProjectAccess(authId, projectId)
        return findConnection(projectId, connectionId).toResponse()
    }

    /** Returns decrypted credentials only for internal Confluence client construction. */
    @Transactional(readOnly = true)
    fun getClientCredentials(authId: String, projectId: UUID, connectionId: UUID): ConfluenceClientCredentials {
        requireProjectAccess(authId, projectId)
        val credential = credentialRepository.findByConnectionIdAndConnectionProjectId(connectionId, projectId)
            ?: throw ConfluenceConnectionNotFoundException(connectionId, projectId)
        return ConfluenceClientCredentials(credential.email, credential.apiToken)
    }

    private fun requireProjectAccess(authId: String, projectId: UUID) {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ConfluenceProjectAccessDeniedException(projectId)
        }
    }

    private fun rejectDuplicate(projectId: UUID, baseUrl: String, spaceId: String) {
        if (connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(projectId, baseUrl, spaceId)) {
            throw ConfluenceConnectionAlreadyExistsException(projectId, spaceId)
        }
    }

    private fun findConnection(projectId: UUID, connectionId: UUID): ConfluenceSpaceConnection {
        return connectionRepository.findByIdAndProjectId(connectionId, projectId)
            ?: throw ConfluenceConnectionNotFoundException(connectionId, projectId)
    }

    private fun validatedCredentials(rawEmail: String, rawApiToken: String): ConfluenceClientCredentials {
        val email = rawEmail.trim()
        val apiToken = rawApiToken.trim()
        if (email.isEmpty()) {
            throw ConfluenceConnectionConfigurationException("Confluence credential email must not be blank")
        }
        if (apiToken.isEmpty()) {
            throw ConfluenceConnectionConfigurationException("Confluence API token must not be blank")
        }
        return ConfluenceClientCredentials(email, apiToken)
    }

    private suspend fun retrieveSpace(
        baseUrl: String,
        credentials: ConfluenceClientCredentials,
        spaceId: String,
    ) = try {
        confluenceClient.getSpace(baseUrl, credentials, spaceId)
    } catch (@Suppress("SwallowedException") exception: ConfluenceAuthenticationException) {
        throw ConfluenceConnectionConfigurationException("Confluence credentials were rejected", httpStatus = 401)
    } catch (@Suppress("SwallowedException") exception: ConfluenceAccessDeniedException) {
        throw ConfluenceConnectionConfigurationException("Confluence space access was denied", httpStatus = 403)
    } catch (@Suppress("SwallowedException") exception: ConfluenceResourceNotFoundException) {
        throw ConfluenceConnectionConfigurationException("Confluence space $spaceId was not found", httpStatus = 404)
    } catch (@Suppress("SwallowedException") exception: ConfluenceExternalServiceException) {
        throw ConfluenceConnectionConfigurationException("Confluence space validation failed", httpStatus = 502)
    } catch (@Suppress("SwallowedException") exception: ConfluenceInvalidResponseException) {
        throw ConfluenceConnectionConfigurationException("Confluence returned invalid space metadata", httpStatus = 502)
    }

    private fun normalizeSpaceId(rawSpaceId: String): String {
        val spaceId = rawSpaceId.trim()
        if (!spaceId.matches(NUMERIC_SPACE_ID)) {
            throw ConfluenceConnectionConfigurationException("Confluence space ID must be numeric")
        }
        return spaceId
    }

    private companion object {
        val NUMERIC_SPACE_ID = Regex("^[0-9]+$")
    }
}
