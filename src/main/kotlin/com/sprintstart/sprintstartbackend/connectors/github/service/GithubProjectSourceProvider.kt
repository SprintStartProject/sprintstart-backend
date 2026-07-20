package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceDto
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Provides project-scoped source summaries for GitHub repository connections.
 *
 * GitHub owns the repository connection state and project association data, so this adapter
 * converts that internal model into the connector overview source DTO used by project APIs.
 */
@Service
class GithubProjectSourceProvider(
    private val repository: GithubRepositoryConnectionRepository,
) : ProjectSourceProvider {
    /**
     * Returns GitHub repositories linked to the given project.
     *
     * @param projectId The project whose GitHub sources should be listed.
     * @return GitHub repository source summaries for the project.
     */
    @Transactional(readOnly = true)
    override fun findSourcesByProjectId(projectId: UUID): List<ProjectSourceDto> {
        return repository.findAllByProjectId(projectId).map { it.toProjectSourceDto() }
    }

    private fun GithubRepositoryConnection.toProjectSourceDto(): ProjectSourceDto {
        return ProjectSourceDto(
            id = id.toString(),
            name = name,
            type = "GITHUB",
            status = toProjectSourceStatus(),
        )
    }

    private fun GithubRepositoryConnection.toProjectSourceStatus(): String {
        if (!sourceEnabled) {
            return "DISABLED"
        }

        return when (connectionState) {
            ConnectionState.UP_TO_DATE -> "CONNECTED"
            ConnectionState.UPDATING -> "UPDATING"
            ConnectionState.OUT_OF_DATE -> "OUT_OF_DATE"
            ConnectionState.FAILED -> "FAILED"
        }
    }
}
