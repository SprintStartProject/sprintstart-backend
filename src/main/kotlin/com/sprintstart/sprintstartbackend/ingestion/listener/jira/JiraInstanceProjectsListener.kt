package com.sprintstart.sprintstartbackend.ingestion.listener.jira

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.projects.JiraInstanceProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Carries a Jira instance's project links through to its artifacts and the AI index.
 */
@Component
internal class JiraInstanceProjectsListener(
    private val artifactProjectService: ArtifactProjectService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Re-scopes the instance's artifacts once the instance change is committed.
     *
     * The Jira counterpart to the GitHub repository listener; see it for why this runs after commit
     * and off the request thread.
     *
     * @param event The instance's project link change.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: JiraInstanceProjectLinkChangedEvent) {
        applicationScope.launch {
            try {
                artifactProjectService.applyProjectLink(
                    ArtifactSourceRef.JiraInstance(event.instanceUrl),
                    event.projectId,
                    event.linked,
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to propagate project {} ({}) for Jira instance {} to the AI index",
                    event.projectId,
                    if (event.linked) "linked" else "unlinked",
                    event.instanceUrl,
                    e,
                )
            }
        }
    }
}
