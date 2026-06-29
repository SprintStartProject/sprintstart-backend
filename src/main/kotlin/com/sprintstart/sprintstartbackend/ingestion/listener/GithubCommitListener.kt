package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionStatusService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubCommitListener(
    private val artifactIngestionService: ArtifactIngestionService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubArtifactFailedMapper: GithubArtifactFailedMapper,
    private val ingestionStatusService: IngestionStatusService,
) {
    @EventListener
    fun on(
        event: GithubCommitFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubCommitsFetchCompletedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.COMMITS,
        )
    }

    @EventListener
    fun on(
        event: GithubCommitFetchFailedEvent,
    ) {
        artifactIngestionService.addFailedArtifact(githubArtifactFailedMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubCommitsFetchFailedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.COMMITS,
        )
    }
}
