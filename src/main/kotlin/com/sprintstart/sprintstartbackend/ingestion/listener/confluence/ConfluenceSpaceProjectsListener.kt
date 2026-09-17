package com.sprintstart.sprintstartbackend.ingestion.listener.confluence

import com.sprintstart.sprintstartbackend.connectors.confluence.external.events.projects.ConfluenceSpaceConnectionDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Takes a deleted Confluence connection's pages out of the project that owned it.
 */
@Component
internal class ConfluenceSpaceProjectsListener(
    private val artifactProjectService: ArtifactProjectService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Unlinks the connection's pages once its deletion is committed.
     *
     * The Confluence counterpart to the GitHub and Jira listeners; see the GitHub one for why this
     * runs after commit, off the request thread, and with `fallbackExecution`.
     *
     * The pages themselves are kept. A Confluence connection belongs to one project, so in practice
     * this leaves them belonging to nothing and reachable from nowhere -- but deleting them here
     * would be wrong all the same: reconnecting the space is meant to find them again rather than
     * pay to fetch and embed every page a second time.
     *
     * @param event The deleted connection and the project that owned it.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: ConfluenceSpaceConnectionDeletedEvent) {
        applicationScope.launch {
            try {
                artifactProjectService.applyProjectLink(
                    ArtifactSourceRef.ConfluenceConnection(event.connectionId),
                    event.projectId,
                    linked = false,
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to unlink project {} from the pages of deleted Confluence connection {}",
                    event.projectId,
                    event.connectionId,
                    e,
                )
            }
        }
    }
}
