package com.sprintstart.sprintstartbackend.ingestion.listener.user

import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Clears a deleted project out of the artifact store and the AI index.
 */
@Component
internal class ProjectDeletedListener(
    private val artifactProjectService: ArtifactProjectService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Drops the project's membership once its deletion is committed.
     *
     * @param event The deleted project.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ProjectDeletedEvent) {
        applicationScope.launch {
            try {
                artifactProjectService.purgeProject(event.projectId)
            } catch (e: Exception) {
                logger.error(
                    "Failed to purge deleted project {} from the artifact store",
                    event.projectId,
                    e,
                )
            }
        }
    }
}
