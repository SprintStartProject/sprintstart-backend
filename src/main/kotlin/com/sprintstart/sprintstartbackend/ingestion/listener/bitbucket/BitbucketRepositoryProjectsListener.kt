package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.projects.BitbucketRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

/**
 * Carries a repository's project links through to its artifacts and the AI index.
 *
 * The workspace metadata artifact follows the same link change: it is visible to a project when
 * any repository of its workspace is, so linking a repository links the workspace artifact too.
 * Unlinking removes the project from the workspace artifact only when no remaining repository of
 * the workspace still carries it — otherwise one repository's unlink would hide the workspace
 * from a project that still has repositories in it.
 */
@Component
internal class BitbucketRepositoryProjectsListener(
    private val artifactProjectService: ArtifactProjectService,
    private val bitbucketRepositoryApi: BitbucketRepositoryApi,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Re-scopes the repository's artifacts once the connection change is committed.
     *
     * Runs after commit so the stored links are already durable, and off the request thread so a
     * slow AI service does not hold up the caller. The operation is idempotent, so repeating the
     * link or unlink repairs a propagation that failed here.
     *
     * `fallbackExecution` is what makes this run when nothing is committing, and it is not
     * defensive: `connectRepositoryIfExists` announces the reuse of an already-connected repository
     * from a `@Transactional` *suspending* function, and Spring opens no transaction for one of
     * those against a JPA transaction manager. Without the fallback the event is dropped and the
     * repository is linked to a project its content is not reachable from — silently, because the
     * connect itself succeeds.
     *
     * @param event The repository's project link change.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: BitbucketRepositoryProjectLinkChangedEvent) {
        applicationScope.launch {
            try {
                artifactProjectService.applyProjectLink(
                    ArtifactSourceRef.BitbucketRepository(event.workspace, event.slug),
                    event.projectId,
                    event.linked,
                )
                applyWorkspaceLink(event.workspace, event.projectId, event.linked)
            } catch (e: Exception) {
                logger.error(
                    "Failed to propagate project {} ({}) for repository {}/{} to the AI index",
                    event.projectId,
                    if (event.linked) "linked" else "unlinked",
                    event.workspace,
                    event.slug,
                    e,
                )
            }
        }
    }

    /**
     * Carries a repository link change through to its workspace metadata artifact.
     *
     * Runs after the repository's own propagation, in the same guarded block, so a workspace
     * re-scope failure is logged rather than reported as a repository failure.
     *
     * @param workspace The workspace whose metadata artifact should follow the link change.
     * @param projectId The project that was linked or unlinked.
     * @param linked Whether the repository was linked or unlinked.
     */
    private suspend fun applyWorkspaceLink(workspace: String, projectId: UUID, linked: Boolean) {
        if (linked) {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketWorkspace(workspace),
                projectId,
                true,
            )
            return
        }
        if (projectId !in bitbucketRepositoryApi.getWorkspaceProjectIds(workspace)) {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketWorkspace(workspace),
                projectId,
                false,
            )
        }
    }
}
