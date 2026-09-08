package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceDto
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceProvider
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Provides project-scoped source summaries for connected Jira instances.
 *
 * The Jira counterpart to
 * [com.sprintstart.sprintstartbackend.connectors.github.service.GithubProjectSourceProvider]. Without
 * it the connector overview only ever reported GitHub repositories, so Jira instances linked to a
 * project were missing from the project source lists (admin projects, project details and the
 * project switcher). The status vocabulary and the disabled-source folding are already applied by
 * [JiraInstanceApi.getSourceInstances], so this adapter only reshapes the module-facing DTO into the
 * connector-neutral [ProjectSourceDto]. Jira instances are identified by their URL, which becomes the
 * source id.
 */
@Service
class JiraProjectSourceProvider(
    private val jiraInstanceApi: JiraInstanceApi,
) : ProjectSourceProvider {
    /**
     * Returns Jira instances linked to the given project.
     *
     * @param projectId The project whose Jira sources should be listed.
     * @return Jira instance source summaries for the project.
     */
    override fun findSourcesByProjectId(projectId: UUID): List<ProjectSourceDto> {
        return jiraInstanceApi.getSourceInstances(projectId).map { instance ->
            ProjectSourceDto(
                id = instance.instanceUrl,
                name = instance.displayName,
                type = "JIRA",
                status = instance.status,
            )
        }
    }
}
