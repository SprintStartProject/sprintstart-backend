package com.sprintstart.sprintstartbackend.connectors.overview.external

import java.util.UUID

/**
 * Exposes project-scoped connected source summaries to other modules.
 *
 * Implementations aggregate source information owned by connector modules so callers do
 * not need to reach into connector repositories directly.
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

/**
 * Provides project-scoped source summaries for one connector family.
 *
 * Connector modules implement this interface and are aggregated by [ProjectSourceApi].
 */
interface ProjectSourceProvider {
    /**
     * Returns source summaries for one project.
     *
     * @param projectId The project whose connector sources should be returned.
     * @return Source summaries owned by this provider.
     */
    fun findSourcesByProjectId(projectId: UUID): List<ProjectSourceDto>
}

data class ProjectSourceDto(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
)
