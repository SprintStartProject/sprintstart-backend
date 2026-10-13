package com.sprintstart.sprintstartbackend.connectors.notion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionDiscoveredPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionWorkspaceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection

internal fun NotionCredential.toResponse(): NotionCredentialResponse {
    return NotionCredentialResponse(
        name = id.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        workspaceId = workspaceId,
        workspaceName = workspaceName,
    )
}

internal fun NotionWorkspaceConnection.toResponse(): NotionWorkspaceConnectionResponse {
    return NotionWorkspaceConnectionResponse(
        id = id,
        projectId = projectId,
        workspaceId = workspaceId,
        workspaceName = workspaceName,
        workspaceUrl = workspaceUrl,
        credentialName = credentialName,
        sourceEnabled = sourceEnabled,
        autoUpdate = autoUpdate,
        schedule = schedule,
        scheduleSpec = scheduleSpec,
        nextSyncAt = nextSyncAt,
        lastSyncedAt = lastSyncedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )
}

internal fun NotionApiPageResponse.toDiscoveredPageResponse(): NotionDiscoveredPageResponse {
    var title = properties.values
        .firstOrNull { property -> property.type == "title" }
        ?.title
        ?.map { it.plainText }
        ?.joinToString(separator = "")
        ?.trim()
        ?: "Untitled"
    title = title.ifBlank { "Untitled" }
    return NotionDiscoveredPageResponse(
        id = id,
        title = title,
        url = url,
        lastEditedTime = lastEditedTime,
    )
}
