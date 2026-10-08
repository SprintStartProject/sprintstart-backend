package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Records the ingestion run for the lifecycle of a Bitbucket repository connection.
 *
 * Mirrors [com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryConnectionListener]:
 * the initiated event opens the run, an already-connected repository closes it as empty, and a
 * failed connection records the failure. The repository id is resolved from the event's coordinates
 * through the Bitbucket module API, so the run is attributed to the connection even when the
 * collectors never report in.
 */
@Component
internal class BitbucketConnectionListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
    private val bitbucketRepositoryApi: BitbucketRepositoryApi,
) {
    @EventListener
    fun on(event: BitbucketRepositoryConnectionInitiatedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            status = IngestionRunStatus.CONNECTED,
            sourceInstanceId = resolveRepositoryId(event.workspace, event.slug),
            sourceInstanceRef = "${event.workspace}/${event.slug}",
        )
    }

    @EventListener
    fun on(event: BitbucketRepositoryAlreadyConnectedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            status = IngestionRunStatus.CONNECTED,
            sourceInstanceId = resolveRepositoryId(event.workspace, event.slug),
            sourceInstanceRef = "${event.workspace}/${event.slug}",
        )
        ingestionRunLifeCycleService.finishEmptyRun(event.transactionId)
    }

    @EventListener
    fun on(event: BitbucketRepositoryConnectionFailedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            status = IngestionRunStatus.FAILED,
            failureReason = event.reason,
            sourceInstanceId = resolveRepositoryId(event.workspace, event.slug),
            sourceInstanceRef = "${event.workspace}/${event.slug}",
        )
    }

    private fun resolveRepositoryId(
        workspace: String,
        slug: String,
    ): UUID? = bitbucketRepositoryApi.getRepositoryIdByWorkspaceAndSlug(workspace, slug)
}
