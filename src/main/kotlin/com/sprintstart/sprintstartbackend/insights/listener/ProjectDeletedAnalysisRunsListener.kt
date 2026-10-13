package com.sprintstart.sprintstartbackend.insights.listener

import com.sprintstart.sprintstartbackend.insights.service.ProjectAnalysisRunService
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Deletes a deleted project's analysis history.
 *
 * The runs only carry the project's id, with no foreign key to the project, so nothing else would
 * ever clear them.
 */
@Component
class ProjectDeletedAnalysisRunsListener(
    private val projectAnalysisRunService: ProjectAnalysisRunService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Drops the project's runs once its deletion is committed. A failure is logged rather than
     * thrown: the project is gone either way, and the leftover rows are only unreachable history.
     *
     * @param event The deleted project.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ProjectDeletedEvent) {
        try {
            projectAnalysisRunService.deleteForProject(event.projectId)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Failed to delete the analysis runs of deleted project {}", event.projectId, e)
        }
    }
}
