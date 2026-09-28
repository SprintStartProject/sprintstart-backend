package com.sprintstart.sprintstartbackend.user.external.events

import java.util.UUID

/**
 * Published when a project is deleted.
 *
 * The project id outlives the project row in several places: connected sources reference it, every
 * artifact of those sources carries it in `artifact_projects`, and every indexed chunk carries a
 * membership marker for it. Modules holding such a reference clean it up in reaction to this event
 * rather than the user module reaching into them.
 *
 * @property projectId The project that was deleted.
 */
data class ProjectDeletedEvent(
    val projectId: UUID,
)
