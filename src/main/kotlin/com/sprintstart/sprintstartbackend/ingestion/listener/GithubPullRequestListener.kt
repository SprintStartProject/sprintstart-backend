package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionStatusService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubPullRequestListener(
    private val artifactIngestionService: ArtifactIngestionService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val ingestionStatusService: IngestionStatusService,
) {
    @EventListener
    fun on(
        event: GithubPullRequestFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubPullRequestsFetchCompletedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.PULL_REQUESTS,
        )
    }

    @EventListener
    fun on(
        event: GithubPullRequestsFetchFailedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.PULL_REQUESTS,
        )
    }
}
