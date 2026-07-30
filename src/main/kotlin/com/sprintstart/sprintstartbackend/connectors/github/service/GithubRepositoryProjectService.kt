package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Links already-connected GitHub repositories to additional projects.
 *
 * The repository connection model already supports membership in several projects
 * (`projectIdsInternal` is a set), so this only adds the project id to an existing connection. It
 * performs no fetching or re-ingestion: the repository's artifacts are shared across the projects it
 * is linked to.
 */
@Service
class GithubRepositoryProjectService(
    private val githubRepositoryConnectionRepository: GithubRepositoryConnectionRepository,
    private val userApi: UserApi,
) {
    /**
     * Adds a project to an already-connected repository, without fetching or re-ingesting anything.
     *
     * The operation is idempotent: linking a project that is already linked leaves the set of
     * projects unchanged.
     *
     * @param authId The authenticated caller subject, used to authorize access to the target project.
     * @param repositoryId The connected repository to link.
     * @param projectId The project to add to the repository's linked projects.
     * @return The resulting set of project ids linked to the repository.
     * @throws ProjectAccessDeniedException when the caller has no access to the target project.
     * @throws RepositoryNotFoundException when no repository connection exists for the given id.
     */
    @Transactional
    @Tracked("Linking GitHub repository to an additional project")
    fun addProjectToRepository(authId: String, repositoryId: UUID, projectId: UUID): Set<UUID> {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ProjectAccessDeniedException(projectId)
        }

        val connection = githubRepositoryConnectionRepository.findById(repositoryId).orElseThrow {
            RepositoryNotFoundException("", "", "Repository connection with id $repositoryId not found")
        }

        connection.projectIdsInternal.add(projectId)
        githubRepositoryConnectionRepository.save(connection)
        return connection.projectIds
    }

    /**
     * Removes a project from an already-connected repository, completing the link lifecycle.
     *
     * The operation is idempotent: unlinking a project that is not linked leaves the set of
     * projects unchanged. The repository connection and its artifacts are kept; only the project
     * association is dropped.
     *
     * @param authId The authenticated caller subject, used to authorize access to the target project.
     * @param repositoryId The connected repository to unlink.
     * @param projectId The project to remove from the repository's linked projects.
     * @return The resulting set of project ids linked to the repository.
     * @throws ProjectAccessDeniedException when the caller has no access to the target project.
     * @throws RepositoryNotFoundException when no repository connection exists for the given id.
     */
    @Transactional
    @Tracked("Unlinking GitHub repository from a project")
    fun removeProjectFromRepository(authId: String, repositoryId: UUID, projectId: UUID): Set<UUID> {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ProjectAccessDeniedException(projectId)
        }

        val connection = githubRepositoryConnectionRepository.findById(repositoryId).orElseThrow {
            RepositoryNotFoundException("", "", "Repository connection with id $repositoryId not found")
        }

        connection.projectIdsInternal.remove(projectId)
        githubRepositoryConnectionRepository.save(connection)
        return connection.projectIds
    }
}
