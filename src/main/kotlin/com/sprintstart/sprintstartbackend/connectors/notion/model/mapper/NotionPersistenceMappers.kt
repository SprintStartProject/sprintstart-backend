package com.sprintstart.sprintstartbackend.connectors.notion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection

internal fun NotionCredential.toResponse(): NotionCredentialResponse {
    return NotionCredentialResponse(
        name = id.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal fun NotionPageConnection.toResponse(): NotionPageConnectionResponse {
    return NotionPageConnectionResponse(
        id = id,
        projectId = projectId,
        pageId = pageId,
        pageTitle = pageTitle,
        pageUrl = pageUrl,
        credentialName = credentialName,
        sourceEnabled = sourceEnabled,
        autoUpdate = autoUpdate,
        schedule = schedule,
        scheduleSpec = scheduleSpec,
        nextSyncAt = nextSyncAt,
        lastEditedTime = lastEditedTime,
        contentHash = contentHash,
        lastSyncedAt = lastSyncedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )
}
