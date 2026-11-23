package com.sprintstart.sprintstartbackend.connectors.git.github.listener

import com.sprintstart.sprintstartbackend.connectors.git.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Unlinks a deleted project from every GitHub repository connection within the transaction deleting the project.
 *
 * Running before commit keeps the unlinking and the deletion atomic, so no GitHub repository connection keeps
 * referencing the deleted project.
 */
@Component
internal class GithubProjectDeletedListener(
    private val api: GithubRepositoryApi,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(event: ProjectDeletedEvent) {
        api.removeProjectFromAllRepositories(event.projectId)
    }
}
