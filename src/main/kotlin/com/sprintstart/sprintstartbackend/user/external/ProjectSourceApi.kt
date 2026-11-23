package com.sprintstart.sprintstartbackend.user.external

import java.util.UUID

/**
 * Supplies the connected sources of a project to the project views of this module.
 *
 * This module owns the interface and the connectors module implements it, aggregating the sources
 * of every connector family, so the dependency points from the connectors to this module.
 */
interface ProjectSourceApi {
    /**
     * Returns all connected sources associated with the given project.
     *
     * @param projectId The project whose connected sources should be returned.
     * @return Source summaries from all registered source providers.
     */
    fun findSourcesByProjectId(projectId: UUID): List<ProjectSourceDto>
}

data class ProjectSourceDto(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
)
