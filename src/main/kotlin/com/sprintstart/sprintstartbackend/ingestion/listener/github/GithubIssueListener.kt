package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubIssueListener(
    private val githubArtifactProviderService: GithubArtifactProviderService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubIngestionRunService: GithubIngestionRunService,
) {
    @EventListener
    fun on(
        event: GithubIssueFetchedEvent,
    ) {
        githubArtifactProviderService.persistArtifact(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubIssuesFetchCompletedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.ISSUES,
        )
    }

    @EventListener
    fun on(
        event: GithubIssuesFetchFailedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.ISSUES,
        )
    }
}
