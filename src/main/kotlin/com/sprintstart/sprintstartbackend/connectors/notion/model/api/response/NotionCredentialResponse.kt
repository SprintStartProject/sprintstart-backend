package com.sprintstart.sprintstartbackend.connectors.notion.model.api.response

import java.time.Instant

/**
 * Represents safe metadata for one stored Notion credential.
 *
 * The encrypted PAT and token-owner identity are intentionally excluded from API responses.
 */
data class NotionCredentialResponse(
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val workspaceId: String? = null,
    val workspaceName: String? = null,
)
