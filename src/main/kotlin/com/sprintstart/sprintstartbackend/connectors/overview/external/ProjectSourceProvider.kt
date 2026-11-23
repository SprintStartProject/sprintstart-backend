package com.sprintstart.sprintstartbackend.connectors.overview.external

import com.sprintstart.sprintstartbackend.user.external.ProjectSourceDto
import java.util.UUID

/**
 * Provides project-scoped source summaries for one connector family.
 *
 * Connector modules implement this interface, and the overview aggregates them into the user
 * module's [ProjectSourceApi][com.sprintstart.sprintstartbackend.user.external.ProjectSourceApi].
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
