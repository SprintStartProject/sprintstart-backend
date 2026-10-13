package com.sprintstart.sprintstartbackend.ingestion.listener.notion

import com.sprintstart.sprintstartbackend.connectors.notion.external.events.projects.NotionWorkspaceConnectionDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Removes a deleted Notion connection's artifacts from its former project after commit.
 *
 * Unlinking is delegated through the ingestion service so artifact membership and indexed chunks
 * change together. Listener failure is logged because the connection transaction has already
 * committed and cannot be rolled back.
 */
@Component
internal class NotionPageProjectsListener(
    private val artifactProjectService: ArtifactProjectService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: NotionWorkspaceConnectionDeletedEvent) {
        applicationScope.launch {
            try {
                artifactProjectService.applyProjectLink(
                    ArtifactSourceRef.NotionConnection(event.connectionId),
                    event.projectId,
                    linked = false,
                )
            } catch (exception: Exception) {
                logger.error(
                    "Failed to unlink project {} from pages of deleted Notion connection {}",
                    event.projectId,
                    event.connectionId,
                    exception,
                )
            }
        }
    }
}
