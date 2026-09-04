package com.sprintstart.sprintstartbackend.connectors.confluence.event

import java.util.UUID

/** Requests initial ingestion after a Confluence connection transaction commits successfully. */
internal data class ConfluenceConnectionCreatedEvent(
    val projectId: UUID,
    val connectionId: UUID,
)
