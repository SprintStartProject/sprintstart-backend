package com.sprintstart.sprintstartbackend.connectors.notion.model.api.response

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import java.time.Instant
import java.util.UUID

data class NotionPageConnectionResponse(
    val id: UUID,
    val projectId: UUID,
    val pageId: String,
    val pageTitle: String,
    val pageUrl: String,
    val credentialName: String,
    val sourceEnabled: Boolean,
    val autoUpdate: Boolean,
    val schedule: String,
    val scheduleSpec: ScheduleSpec,
    val nextSyncAt: Instant?,
    val lastEditedTime: Instant?,
    val contentHash: String?,
    val lastSyncedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
