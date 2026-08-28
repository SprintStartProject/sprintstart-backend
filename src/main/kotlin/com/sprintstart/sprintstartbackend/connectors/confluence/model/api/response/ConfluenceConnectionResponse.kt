package com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/** Safe external representation of a persisted Confluence space connection. */
@Schema(description = "Project-scoped Confluence space connection without credential material.")
data class ConfluenceConnectionResponse(
    val id: UUID,
    val projectId: UUID,
    val baseUrl: String,
    val spaceId: String,
    val spaceKey: String,
    val pageAllowlist: List<String>,
    val pageDenylist: List<String>,
    @field:Schema(description = "Whether encrypted credentials are configured; no credential value is returned.")
    val credentialsConfigured: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
    val sourceEnabled: Boolean = true,
    val autoUpdate: Boolean = false,
    val spec: ScheduleSpec,
    val schedule: String,
    val nextSyncAt: Instant?,
)
