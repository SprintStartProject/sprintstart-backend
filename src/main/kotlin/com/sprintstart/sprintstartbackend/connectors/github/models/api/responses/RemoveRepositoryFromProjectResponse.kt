package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

/**
 * Response for unlinking a GitHub repository from a project.
 *
 * Returns the repository connection and the resulting set of linked project ids so the frontend can
 * update its project associations optimistically.
 */
data class RemoveRepositoryFromProjectResponse(
    val repositoryId: UUID,
    val projectIds: Set<UUID>,
)
