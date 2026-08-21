package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.projects.GithubRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Carries a repository's project links through to its artifacts and the AI index.
 */
@Component
internal class GithubRepositoryProjectsListener(
    private val artifactProjectService: ArtifactProjectService,
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
     * @param event The repository's project link change.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: GithubRepositoryProjectLinkChangedEvent) {
        applicationScope.launch {
            try {
                artifactProjectService.applyProjectLink(
                    ArtifactSourceRef.GithubRepository(event.owner, event.name),
                    event.projectId,
                    event.linked,
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to propagate project {} ({}) for repository {}/{} to the AI index",
                    event.projectId,
                    if (event.linked) "linked" else "unlinked",
                    event.owner,
                    event.name,
                    e,
                )
            }
        }
    }
}
