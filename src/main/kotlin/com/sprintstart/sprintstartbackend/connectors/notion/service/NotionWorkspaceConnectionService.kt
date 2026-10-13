package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.external.events.projects.NotionWorkspaceConnectionDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ConfigureNotionScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.CreateNotionWorkspaceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionDiscoveredPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionWorkspaceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.notion.model.mapper.toDiscoveredPageResponse
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Validates and manages project-scoped Notion PAT permission-scope connections.
 *
 * One connection represents all ordinary pages visible to a stored credential rather than one
 * selected page. Persistence commits before initial ingestion is launched in the background.
 */
@Service
internal class NotionWorkspaceConnectionService(
    private val notionClient: NotionClient,
    private val credentialPersistenceService: NotionCredentialPersistenceService,
    private val connectionPersistenceService: NotionWorkspaceConnectionPersistenceService,
    private val cronBuilder: CronBuilder,
    private val scheduleCalculator: NotionScheduleCalculator,
    private val scheduledExecutor: ScheduledExecutor,
    private val ingestionService: NotionWorkspaceIngestionService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** Lists ordinary pages currently visible to a stored credential as a read-only preview. */
    suspend fun discoverPages(
        authId: String,
        credentialName: String,
    ): List<NotionDiscoveredPageResponse> {
        val token = withContext(Dispatchers.IO) {
            credentialPersistenceService.requireToken(authId, credentialName.trim())
        }
        return notionClient.discoverPages(token).map { it.toDiscoveredPageResponse() }
    }

    /**
     * Creates one connection for a credential's visible Notion scope and starts initial ingestion.
     *
     * The remote identity is refreshed before persistence. The scheduled task starts only after
     * [NotionWorkspaceConnectionPersistenceService.persist] has committed.
     */
    suspend fun connectWorkspace(
        authId: String,
        projectId: UUID,
        request: CreateNotionWorkspaceConnectionRequest,
    ): NotionWorkspaceConnectionResponse {
        val credentialName = request.credentialName.trim()
        val token = withContext(Dispatchers.IO) {
            credentialPersistenceService.requireToken(authId, credentialName)
        }
        val workspace = notionClient.validateConnection(token)
        withContext(Dispatchers.IO) {
            credentialPersistenceService.recordTokenIdentity(authId, credentialName, workspace)
        }

        val connection = NotionWorkspaceConnection(
            projectId = projectId,
            workspaceId = workspace.workspaceId,
            workspaceName = workspace.workspaceName ?: credentialName,
            tokenOwnerId = workspace.tokenOwnerId,
            credentialAuthId = authId,
            credentialName = credentialName,
        )
        val saved = withContext(Dispatchers.IO) {
            connectionPersistenceService.persist(connection)
        }
        scheduledExecutor.launch("Initial ingestion for Notion connection '${saved.id}'") {
            ingestionService.ingest(projectId, saved.id)
        }
        return saved
    }

    fun getConnections(projectId: UUID): List<NotionWorkspaceConnectionResponse> {
        return connectionPersistenceService.findAll(projectId)
    }

    /** Updates automatic synchronization settings for one project-owned connection. */
    fun configureSchedule(
        projectId: UUID,
        connectionId: UUID,
        request: ConfigureNotionScheduleRequest,
    ): NotionWorkspaceConnectionResponse {
        val schedule = cronBuilder.build(request.schedule)
        val calculatedNextSyncAt = scheduleCalculator.calculateNextSyncAt(schedule, Instant.now())
            ?: throw NotionWorkspaceConnectionConfigurationException("Notion schedule is invalid")
        return connectionPersistenceService.configureSchedule(
            projectId = projectId,
            connectionId = connectionId,
            scheduleSpec = request.schedule,
            schedule = schedule,
            autoUpdate = request.autoUpdate,
            nextSyncAt = if (request.autoUpdate) calculatedNextSyncAt else null,
        )
    }

    /**
     * Deletes a connection and announces that its artifacts must leave the project.
     *
     * Per-page state is removed transactionally first; the event lets the ingestion module unlink
     * canonical artifacts and indexed chunks without direct repository access across modules.
     */
    fun deleteConnection(projectId: UUID, connectionId: UUID) {
        connectionPersistenceService.delete(projectId, connectionId)
        eventPublisher.publishEvent(NotionWorkspaceConnectionDeletedEvent(connectionId, projectId))
    }
}
