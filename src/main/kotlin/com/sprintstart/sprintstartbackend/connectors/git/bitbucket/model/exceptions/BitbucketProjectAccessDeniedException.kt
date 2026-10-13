package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions

import java.util.UUID

/**
 * Raised when a caller tries to act on a project they have no access to.
 *
 * The message names the project but not the caller's relation to it, so it leaks nothing about who
 * may see a project.
 *
 * @property projectId The project access was requested for.
 */
data class BitbucketProjectAccessDeniedException(
    val projectId: UUID,
) : RuntimeException("No access to project with id $projectId")
