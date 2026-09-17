package com.sprintstart.sprintstartbackend.connectors.confluence.external.events.projects

import java.util.UUID

/**
 * Emitted when a project's Confluence space connection is deleted.
 *
 * The Confluence counterpart to the GitHub and Jira project-link events, and carried for the same
 * reason: the pages this connection ingested hold the project membership retrieval filters on, so
 * a deletion that stops at the connection row leaves them answerable in a project that no longer
 * has the source.
 *
 * Deletion rather than a link change, because a Confluence connection belongs to exactly one
 * project. There is no lifecycle in which it gains or loses one -- unlinking it *is* deleting it.
 *
 * @property connectionId The deleted connection, which is what its pages carry in their source id.
 * @property projectId The project that owned it.
 */
data class ConfluenceSpaceConnectionDeletedEvent(
    val connectionId: UUID,
    val projectId: UUID,
)
