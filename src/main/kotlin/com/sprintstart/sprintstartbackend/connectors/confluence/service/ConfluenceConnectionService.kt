package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceExternalServiceException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceResourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.ConfigureConfluenceScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Validates and persists project-scoped Confluence Cloud space connections. */
@Service
internal class ConfluenceConnectionService(
    private val confluenceClient: ConfluenceClient,
    private val connectionRepository: ConfluenceSpaceConnectionRepository,
    private val atlassianCredentialApi: AtlassianCredentialApi,
    private val userApi: UserApi,
    private val cronBuilder: CronBuilder,
    private val scheduleCalculator: ConfluenceScheduleCalculator,
    private val connectionPersistenceService: ConfluenceConnectionPersistenceService,
    private val scheduledExecutor: ScheduledExecutor,
    private val ingestionService: ConfluencePageIngestionService,
) {
    /**
     * Validates the selected remote space before atomically storing its connection and credential.
     *
     * The canonical space ID and key come from Confluence. Nothing is persisted when validation fails. Once the
     * connection is stored, its first ingestion is launched in the background just like the GitHub and Jira
     * connectors do after a successful connect, so a new source starts filling up without a manual update.
     *
     * Deliberately not transactional: the space lookup suspends, so the coroutine resumes on another thread where
     * Spring's thread-bound transaction synchronization no longer exists. The write happens in
     * [ConfluenceConnectionPersistenceService.persist] instead, which owns the only transaction of this flow and
     * has committed by the time the initial ingestion starts.
     */
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
        rejectDuplicate(projectId, normalizedBaseUrl, requestedSpaceId)
        val credentials = resolveCredentials(authId, request.credentialName)
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
            spaceName = space.name.trim().ifBlank { null },
            credentialAuthId = authId,
            credentialName = request.credentialName,
            pageAllowlistInternal = allowlist.toMutableList(),
            pageDenylistInternal = denylist.toMutableList(),
        )

        val saved = connectionPersistenceService.persist(connection)
        scheduledExecutor.launch("Initial ingestion for Confluence connection '${saved.id}'") {
            ingestionService.ingest(projectId, saved.id)
        }
        return saved
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

    /** Updates automatic synchronization settings for one project-owned connection. */
    @Transactional
    fun configureSchedule(
        authId: String,
        projectId: UUID,
        connectionId: UUID,
        request: ConfigureConfluenceScheduleRequest,
    ): ConfluenceConnectionResponse {
        requireProjectAccess(authId, projectId)
        val connection = findConnection(projectId, connectionId)
        val schedule = cronBuilder.build(request.schedule)
        val nextSyncAt = scheduleCalculator.calculateNextSyncAt(schedule, Instant.now())
            ?: throw ConfluenceConnectionConfigurationException("Confluence schedule is invalid")

        connection.spec = request.schedule
        connection.schedule = schedule
        connection.autoUpdate = request.autoUpdate
        connection.nextSyncAt = nextSyncAt
        return connection.toResponse()
    }

    /**
     * Removes one project-owned connection, the counterpart to connecting a space.
     *
     * Mirrors the Jira connector's "remove from project" with one structural difference: a Jira
     * instance is shared between projects and only loses the association, while a Confluence
     * connection belongs to exactly one project, so unlinking it is deleting it. The pages it
     * already ingested are kept, as with the Jira and GitHub connectors; only the page filters
     * stored alongside the connection go with it (via their cascade).
     *
     * @throws ConfluenceConnectionNotFoundException when the connection does not belong to the project.
     */
    @Transactional
    fun deleteConnection(authId: String, projectId: UUID, connectionId: UUID) {
        requireProjectAccess(authId, projectId)
        connectionRepository.delete(findConnection(projectId, connectionId))
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

    /** Resolves the shared Atlassian credential a new connection should reference. */
    private fun resolveCredentials(authId: String, credentialName: String): ConfluenceClientCredentials {
        val secret = atlassianCredentialApi.findSecret(authId, credentialName)
            ?: throw ConfluenceCredentialNotFoundException(credentialName)
        return ConfluenceClientCredentials(secret.userEmail, secret.apiToken)
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
