package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Opens or advances the ingestion run when a Bitbucket collector starts fetching.
 *
 * A connect announces its run through the connection events before any collector runs, but a
 * scheduled update launches its collectors directly, so without these handlers the first event an
 * update writes — a fetched artifact or a phase completion — would find no run row. All three
 * collectors of one repository publish the same transaction id and the same coordinates, so
 * whichever handler lands first creates the run and the other two merely re-confirm it.
 */
@Component
internal class BitbucketResourcesListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    @EventListener
    fun on(event: BitbucketFilesFetchingStartedEvent) {
        startRun(event.transactionId, event.repositoryId, event.workspace, event.slug)
    }

    @EventListener
    fun on(event: BitbucketCommitsFetchingStartedEvent) {
        startRun(event.transactionId, event.repositoryId, event.workspace, event.slug)
    }

    @EventListener
    fun on(event: BitbucketPullRequestsFetchingStartedEvent) {
        startRun(event.transactionId, event.repositoryId, event.workspace, event.slug)
    }

    private fun startRun(
        transactionId: UUID,
        repositoryId: UUID,
        workspace: String,
        slug: String,
    ) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            status = IngestionRunStatus.RUNNING,
            sourceInstanceId = repositoryId,
            sourceInstanceRef = "$workspace/$slug",
        )
    }
}
