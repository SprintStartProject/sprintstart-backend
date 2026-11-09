package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.listener

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.user.external.events.ProjectDeletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Unlinks a deleted project from every Bitbucket repository connection within the transaction deleting the project.
 *
 * Running before commit keeps the unlinking and the deletion atomic, so no Bitbucket repository connection keeps
 * referencing the deleted project.
 */
@Component
internal class BitbucketProjectDeletedListener(
    private val api: BitbucketRepositoryApi,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(event: ProjectDeletedEvent) {
        api.removeProjectFromAllRepositories(event.projectId)
    }
}
