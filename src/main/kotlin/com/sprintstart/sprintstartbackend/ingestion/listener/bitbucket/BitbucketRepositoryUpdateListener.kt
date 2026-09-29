package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Records the ingestion run for the lifecycle of a Bitbucket repository update.
 *
 * Mirrors
 * [com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubRepositoryUpdateListener]: an
 * accepted update opens the run, and an update that could not be started fails it. The run is
 * therefore attributed to its repository even when no collector ever reports in — the case the
 * per-collector started events cannot cover, because they never happen.
 *
 * Both events carry the repository id and its coordinates, so this listener resolves nothing through
 * a module API. The GitHub counterpart has to look the repository up from an owner and name it was
 * given instead.
 */
@Component
internal class BitbucketRepositoryUpdateListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    @EventListener
    fun on(event: BitbucketRepositoryUpdateStartedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            status = IngestionRunStatus.CONNECTED,
            sourceInstanceId = event.repositoryId,
            sourceInstanceRef = "${event.workspace}/${event.slug}",
        )
    }

    @EventListener
    fun on(event: BitbucketRepositoryUpdateFailedEvent) {
        ingestionRunLifeCycleService.startOrUpdateRun(
            transactionId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            status = IngestionRunStatus.FAILED,
            failureReason = event.reason,
            sourceInstanceId = event.repositoryId,
            sourceInstanceRef = "${event.workspace}/${event.slug}",
        )
    }
}
