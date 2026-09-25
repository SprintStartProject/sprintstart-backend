package com.sprintstart.sprintstartbackend.connectors.notion.model.exception

import java.util.UUID

internal sealed class NotionPersistenceException(
    message: String,
    val httpStatus: Int,
) : RuntimeException(message)

internal class NotionCredentialAlreadyExistsException(name: String) :
    NotionPersistenceException("Notion credential '$name' already exists", 409)

internal class NotionCredentialNotFoundException(name: String) :
    NotionPersistenceException("Notion credential '$name' was not found", 404)

internal class NotionCredentialStillInUseException(name: String) :
    NotionPersistenceException("Notion credential '$name' is still used by a page connection", 409)

internal class NotionPageConnectionAlreadyExistsException(projectId: UUID, pageId: String) :
    NotionPersistenceException("Notion page $pageId is already connected to project $projectId", 409)

internal class NotionPageConnectionNotFoundException(connectionId: UUID, projectId: UUID) :
    NotionPersistenceException("Notion connection $connectionId was not found in project $projectId", 404)

internal class NotionPageConnectionConfigurationException(message: String) :
    NotionPersistenceException(message, 400)
