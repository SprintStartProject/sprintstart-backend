package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.FailedArtifactService
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionStatusService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubCommitListener(
    private val githubArtifactProviderService: GithubArtifactProviderService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubArtifactFailedMapper: GithubArtifactFailedMapper,
    private val ingestionStatusService: IngestionStatusService,
    private val githubIngestionRunService: GithubIngestionRunService,
    private val failedArtifactService: FailedArtifactService,
) {
    @EventListener
    fun on(
        event: GithubCommitFetchedEvent,
    ) {
        githubArtifactProviderService.persistArtifact(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubCommitsFetchCompletedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.COMMITS,
        )
    }

    @EventListener
    fun on(
        event: GithubCommitFetchFailedEvent,
    ) {
        failedArtifactService.addFailedArtifact(githubArtifactFailedMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubCommitsFetchFailedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.COMMITS,
        )
    }
}
