package com.sprintstart.sprintstartbackend.connectors.jira.listener

import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Unlinks a deleted project from every Jira instance within the transaction deleting the project.
 *
 * Running before commit keeps the unlinking and the deletion atomic, so no Jira instance keeps
 * referencing the deleted project.
 */
@Component
internal class JiraProjectDeletedListener(
    private val api: JiraInstanceApi,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(event: ProjectDeletedEvent) {
        api.removeProjectFromAllInstances(event.projectId)
    }
}
