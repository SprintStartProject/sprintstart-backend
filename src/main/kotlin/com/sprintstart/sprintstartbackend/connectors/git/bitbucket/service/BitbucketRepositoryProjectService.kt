package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.projects.BitbucketRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Links already-connected Bitbucket repositories to additional projects.
 *
 * The repository connection model already supports membership in several projects
 * (`projectIdsInternal` is a set), so this only adds or removes a project id on an existing
 * connection. It performs no fetching or re-ingestion: the repository's artifacts are shared across
 * the projects it is linked to.
 *
 * The stored artifacts and their indexed chunks carry the same membership, and retrieval is
 * fail-closed on it, so each change is announced with a [BitbucketRepositoryProjectLinkChangedEvent]
 * for the ingestion module to follow — including a repeat of an existing link, which is how a
 * caller repairs a propagation that failed earlier.
 *
 * @constructor Creates the service from its dependencies.
 * @param connectionRepository Owns the connection rows the links are written to.
 * @param userApi Answers whether the caller may write to the target project.
 * @param eventPublisher Announces every link change to the ingestion module.
 */
@Service
internal class BitbucketRepositoryProjectService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val userApi: UserApi,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Adds a project to an already-connected repository, without fetching or re-ingesting anything.
     *
     * The operation is idempotent: linking a project that is already linked leaves the set of
     * projects unchanged, and the announcement is still published so a repeat repairs a propagation
     * that failed earlier.
     *
     * @param authId The authenticated caller subject, used to authorize access to the target project.
     * @param repositoryId The connected repository to link.
     * @param projectId The project to add to the repository's linked projects.
     * @return The resulting set of project ids linked to the repository.
     * @throws BitbucketProjectAccessDeniedException when the caller has no access to the target project.
     * @throws BitbucketRepositoryConnectionNotFoundException when no repository connection exists for
     *         the given id.
     */
    @Transactional
    @Tracked("Linking Bitbucket repository to an additional project")
    suspend fun addProjectToRepository(
        authId: String,
        repositoryId: UUID,
        projectId: UUID
    ): Set<UUID> = withContext(Dispatchers.IO) {
        requireProjectAccess(authId, projectId)

        val connection = findConnection(repositoryId)
        connection.projectIdsInternal.add(projectId)
        connectionRepository.save(connection)
        publishLinkChanged(connection, projectId, linked = true)

        return@withContext connection.projectIds
    }

    /**
     * Removes a project from an already-connected repository, completing the link lifecycle.
     *
     * The operation is idempotent: unlinking a project that is not linked leaves the set of projects
     * unchanged. The repository connection and its artifacts are kept; only the project association
     * is dropped.
     *
     * @param authId The authenticated caller subject, used to authorize access to the target project.
     * @param repositoryId The connected repository to unlink.
     * @param projectId The project to remove from the repository's linked projects.
     * @return The resulting set of project ids linked to the repository.
     * @throws BitbucketProjectAccessDeniedException when the caller has no access to the target project.
     * @throws BitbucketRepositoryConnectionNotFoundException when no repository connection exists for
     *         the given id.
     */
    @Transactional
    @Tracked("Unlinking Bitbucket repository from a project")
    suspend fun removeProjectFromRepository(
        authId: String,
        repositoryId: UUID,
        projectId: UUID,
    ): Set<UUID> = withContext(Dispatchers.IO) {
        requireProjectAccess(authId, projectId)

        val connection = findConnection(repositoryId)
        connection.projectIdsInternal.remove(projectId)
        connectionRepository.save(connection)
        publishLinkChanged(connection, projectId, linked = false)

        return@withContext connection.projectIds
    }

    /**
     * Rejects a caller who may not write to the target project.
     *
     * Checked before the connection lookup, so an unauthorized caller cannot use the error to learn
     * which connection ids exist: both refusals answer 4xx, but the access check asks the caller's
     * own relation to the project first.
     *
     * @param authId The authenticated caller subject.
     * @param projectId The project the caller wants to link or unlink.
     * @throws BitbucketProjectAccessDeniedException when the caller has no access.
     */
    private fun requireProjectAccess(authId: String, projectId: UUID) {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw BitbucketProjectAccessDeniedException(projectId)
        }
    }

    /**
     * Resolves one connection by id, or refuses.
     *
     * @param repositoryId The connection id the caller sent.
     * @return The connection.
     * @throws BitbucketRepositoryConnectionNotFoundException when no connection exists for the id.
     */
    private fun findConnection(repositoryId: UUID) =
        connectionRepository.findById(repositoryId).orElseThrow {
            BitbucketRepositoryConnectionNotFoundException(repositoryId)
        }

    /**
     * Announces one link change to the ingestion module.
     *
     * @param connection The connection whose project set changed.
     * @param projectId The project that was linked or unlinked.
     * @param linked `true` when the project was added, `false` when it was removed.
     */
    private fun publishLinkChanged(
        connection: BitbucketConnection,
        projectId: UUID,
        linked: Boolean,
    ) {
        eventPublisher.publishEvent(
            BitbucketRepositoryProjectLinkChangedEvent(
                workspace = connection.workspace,
                slug = connection.slug,
                projectId = projectId,
                linked = linked,
            ),
        )
    }
}
