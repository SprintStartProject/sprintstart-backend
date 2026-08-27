package com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response

import java.time.Instant
import java.util.UUID

/** Safe external representation of a persisted Confluence space connection. */
data class ConfluenceConnectionResponse(
    val id: UUID,
    val projectId: UUID,
    val baseUrl: String,
    val spaceId: String,
    val spaceKey: String,
    val pageAllowlist: List<String>,
    val pageDenylist: List<String>,
    val credentialsConfigured: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
    val sourceEnabled: Boolean = true,
)
