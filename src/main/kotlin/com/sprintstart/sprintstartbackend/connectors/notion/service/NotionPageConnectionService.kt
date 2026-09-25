package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.CreateNotionPageConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPageConnectionConfigurationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class NotionPageConnectionService(
    private val notionClient: NotionClient,
    private val credentialPersistenceService: NotionCredentialPersistenceService,
    private val connectionPersistenceService: NotionPageConnectionPersistenceService,
) {
    suspend fun connectPage(
        authId: String,
        projectId: UUID,
        request: CreateNotionPageConnectionRequest,
    ): NotionPageConnectionResponse {
        val credentialName = request.credentialName.trim()
        val requestedPageId = request.pageId.trim()
        val token = withContext(Dispatchers.IO) {
            credentialPersistenceService.requireToken(authId, credentialName)
        }
        val page = notionClient.getPage(token, requestedPageId)
        if (page.inTrash) {
            throw NotionPageConnectionConfigurationException("Notion page ${page.id} is in trash")
        }
        if (page.parent.type == "data_source_id" || page.parent.type == "database_id") {
            throw NotionPageConnectionConfigurationException("Notion database rows cannot be connected as pages")
        }
        val pageTitle = page.properties.values
            .firstOrNull { it.type == "title" }
            ?.title
            .orEmpty()
            .joinToString(separator = "") { it.plainText }
            .trim()
            .ifEmpty { "Untitled" }

        val connection = NotionPageConnection(
            projectId = projectId,
            pageId = page.id,
            pageTitle = pageTitle,
            pageUrl = page.url,
            credentialAuthId = authId,
            credentialName = credentialName,
        )
        return withContext(Dispatchers.IO) {
            connectionPersistenceService.persist(connection)
        }
    }

    fun getConnections(projectId: UUID): List<NotionPageConnectionResponse> {
        return connectionPersistenceService.findAll(projectId)
    }

    fun deleteConnection(projectId: UUID, connectionId: UUID) {
        connectionPersistenceService.delete(projectId, connectionId)
    }
}
