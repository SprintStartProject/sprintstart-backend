package com.sprintstart.sprintstartbackend.connectors.notion.listener

import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionWorkspaceConnectionPersistenceService
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Removes Notion connections within the transaction deleting their owning project.
 *
 * Running before commit prevents scheduled synchronization from retaining a connection that could
 * later re-link artifacts to a deleted project.
 */
@Component
internal class NotionProjectDeletedListener(
    private val connectionPersistenceService: NotionWorkspaceConnectionPersistenceService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(event: ProjectDeletedEvent) {
        connectionPersistenceService.deleteAllForProject(event.projectId)
    }
}
