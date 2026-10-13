package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceDto
import com.sprintstart.sprintstartbackend.connectors.overview.external.ProjectSourceProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Provides project-scoped source summaries for Bitbucket repository connections.
 *
 * Bitbucket owns the repository connection state and project association data, so this adapter
 * converts that internal model into the connector overview source DTO used by project APIs — the
 * Bitbucket counterpart to
 * [com.sprintstart.sprintstartbackend.connectors.git.github.service.GithubProjectSourceProvider].
 *
 * The reported status goes through [toSourceStatus], the same mapping the ingestion status view
 * uses, so a repository is never reported as connected in one place and disabled in another.
 */
@Service
internal class BitbucketProjectSourceProvider(
    private val repository: BitbucketConnectionRepository,
) : ProjectSourceProvider {
    /**
     * Returns Bitbucket repositories linked to the given project.
     *
     * @param projectId The project whose Bitbucket sources should be listed.
     * @return Bitbucket repository source summaries for the project.
     */
    @Transactional(readOnly = true)
    override fun findSourcesByProjectId(projectId: UUID): List<ProjectSourceDto> {
        return repository.findAllByProjectId(projectId).map { it.toProjectSourceDto() }
    }

    private fun BitbucketConnection.toProjectSourceDto(): ProjectSourceDto {
        return ProjectSourceDto(
            id = id.toString(),
            name = "$workspace/$slug",
            type = "BITBUCKET",
            status = toSourceStatus(),
        )
    }
}
