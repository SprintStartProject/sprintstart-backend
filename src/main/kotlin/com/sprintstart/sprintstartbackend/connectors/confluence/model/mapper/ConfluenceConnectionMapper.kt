package com.sprintstart.sprintstartbackend.connectors.confluence.model.mapper

import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection

internal fun ConfluenceSpaceConnection.toResponse(): ConfluenceConnectionResponse {
    return ConfluenceConnectionResponse(
        id = id,
        projectId = projectId,
        baseUrl = baseUrl,
        spaceId = spaceId,
        spaceKey = spaceKey,
        pageAllowlist = pageAllowlist,
        pageDenylist = pageDenylist,
        credentialsConfigured = true,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        sourceEnabled = sourceEnabled,
    )
}
