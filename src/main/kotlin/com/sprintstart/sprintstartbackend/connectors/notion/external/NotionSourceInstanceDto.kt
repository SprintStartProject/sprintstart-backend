package com.sprintstart.sprintstartbackend.connectors.notion.external

import java.time.Instant
import java.util.UUID

/** Safe module-facing view of one connected Notion page, without credential data. */
data class NotionSourceInstanceDto(
    val connectionId: UUID,
    val sourceRef: String,
    val pageId: String,
    val pageTitle: String,
    val pageUrl: String,
    val status: String,
    val enabled: Boolean,
    val lastSyncedAt: Instant?,
)
