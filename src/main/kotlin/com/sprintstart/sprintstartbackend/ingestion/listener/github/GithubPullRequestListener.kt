package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubPullRequestListener(
    private val githubArtifactProviderService: GithubArtifactProviderService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubIngestionRunService: GithubIngestionRunService,
) {
    @EventListener
    fun on(
        event: GithubPullRequestFetchedEvent,
    ) {
        githubArtifactProviderService.persistArtifact(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubPullRequestsFetchCompletedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.PULL_REQUESTS,
        )
    }

    @EventListener
    fun on(
        event: GithubPullRequestsFetchFailedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.PULL_REQUESTS,
        )
    }
}
