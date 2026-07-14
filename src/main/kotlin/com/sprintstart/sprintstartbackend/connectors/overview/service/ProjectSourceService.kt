package com.sprintstart.sprintstartbackend.connectors.overview.service

import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceApi
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceDto
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceProvider
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Aggregates project-scoped source summaries from connector modules.
 *
 * This service is the module-facing adapter used by other modules. Individual connector
 * modules contribute providers, while callers depend only on the small [ProjectSourceApi].
 */
@Service
class ProjectSourceService(
    private val providers: List<ProjectSourceProvider>,
) : ProjectSourceApi {
    /**
     * Returns all known sources linked to a project.
     *
     * @param projectId The project whose sources should be listed.
     * @return A combined list from all registered source providers.
     */
    override fun findSourcesByProjectId(projectId: UUID): List<ProjectSourceDto> {
        return providers.flatMap { it.findSourcesByProjectId(projectId) }
    }
}
