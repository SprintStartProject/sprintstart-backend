package com.sprintstart.sprintstartbackend.connectors.github.models.exceptions

import java.util.UUID

/**
 * Exception thrown when a caller tries to act on a project they have no access to.
 */
data class ProjectAccessDeniedException(
    val projectId: UUID,
) : RuntimeException("No access to project with id $projectId")
