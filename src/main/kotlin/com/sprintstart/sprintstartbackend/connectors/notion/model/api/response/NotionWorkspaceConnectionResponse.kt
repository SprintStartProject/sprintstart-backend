package com.sprintstart.sprintstartbackend.connectors.notion.model.api.response

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import java.time.Instant
import java.util.UUID

/**
 * Represents one project-scoped Notion permission-scope connection.
 *
 * Workspace fields are descriptive and may fall back to the credential name for personal tokens
 * whose identity response omits bot workspace metadata.
 */
data class NotionWorkspaceConnectionResponse(
    val id: UUID,
    val projectId: UUID,
    val workspaceId: String?,
    val workspaceName: String,
    val workspaceUrl: String,
    val credentialName: String,
    val sourceEnabled: Boolean,
    val autoUpdate: Boolean,
    val schedule: String,
    val scheduleSpec: ScheduleSpec,
    val nextSyncAt: Instant?,
    val lastSyncedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
