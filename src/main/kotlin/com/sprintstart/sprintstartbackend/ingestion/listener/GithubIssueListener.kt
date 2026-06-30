package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionStatusService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubIssueListener(
    private val artifactIngestionService: ArtifactIngestionService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val ingestionStatusService: IngestionStatusService,
) {
    @EventListener
    fun on(
        event: GithubIssueFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubIssuesFetchCompletedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.ISSUES,
        )
    }

    @EventListener
    fun on(
        event: GithubIssuesFetchFailedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.ISSUES,
        )
    }
}
