package com.sprintstart.sprintstartbackend.connectors.confluence.external

import java.util.UUID

/**
 * Safe module-facing view of one configured Confluence Cloud space.
 *
 * [sourceRef] matches the connector-neutral reference persisted on Confluence ingestion runs. The
 * DTO deliberately contains no credential fields.
 */
data class ConfluenceSourceInstanceDto(
    val connectionId: UUID,
    val sourceRef: String,
    val spaceId: String,
    val spaceKey: String,
    val sourceUrl: String,
    val status: String,
    val enabled: Boolean,
)
