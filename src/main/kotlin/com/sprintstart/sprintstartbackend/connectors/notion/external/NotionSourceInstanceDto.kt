package com.sprintstart.sprintstartbackend.connectors.notion.external

import java.time.Instant
import java.util.UUID

/**
 * Provides a safe module-facing view of one connected Notion permission scope.
 *
 * The view contains display and status metadata only; the credential name and PAT are intentionally
 * excluded.
 */
data class NotionSourceInstanceDto(
    val connectionId: UUID,
    val sourceRef: String,
    val workspaceId: String?,
    val workspaceName: String,
    val workspaceUrl: String,
    val status: String,
    val enabled: Boolean,
    val lastSyncedAt: Instant?,
)
