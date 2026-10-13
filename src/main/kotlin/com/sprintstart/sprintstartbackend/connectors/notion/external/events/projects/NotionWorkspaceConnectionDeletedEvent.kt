package com.sprintstart.sprintstartbackend.connectors.notion.external.events.projects

import java.util.UUID

/**
 * Announces that a project's Notion workspace connection has been removed.
 *
 * The event carries the complete source and project identity required by the ingestion module to
 * unlink every artifact belonging to the deleted connection without reading connector repositories.
 */
data class NotionWorkspaceConnectionDeletedEvent(
    val connectionId: UUID,
    val projectId: UUID,
)
