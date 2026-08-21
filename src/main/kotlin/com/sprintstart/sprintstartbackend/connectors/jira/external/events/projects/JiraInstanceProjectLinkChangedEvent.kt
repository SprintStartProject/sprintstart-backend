package com.sprintstart.sprintstartbackend.connectors.jira.external.events.projects

import java.util.UUID

/**
 * Emitted when a connected Jira instance is linked to, or unlinked from, a project.
 *
 * The Jira counterpart to the GitHub connector's repository link event, and carried for the same
 * reason: the instance's artifacts and their indexed chunks hold the membership retrieval filters
 * on, so a link that stops at the instance row is invisible to chat.
 *
 * @property instanceUrl The Jira instance whose project links changed.
 * @property projectId The project that was linked or unlinked.
 * @property linked `true` when the project was added, `false` when it was removed.
 */
data class JiraInstanceProjectLinkChangedEvent(
    val instanceUrl: String,
    val projectId: UUID,
    val linked: Boolean,
)
