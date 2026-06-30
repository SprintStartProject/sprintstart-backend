package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionStatusService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubFileListener(
    private val artifactIngestionService: ArtifactIngestionService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubArtifactFailedMapper: GithubArtifactFailedMapper,
    private val ingestionStatusService: IngestionStatusService,
) {
    @EventListener
    fun on(
        event: GithubFileFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubFilesFetchCompletedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }

    @EventListener
    fun on(
        event: GithubFileFetchFailedEvent,
    ) {
        artifactIngestionService.addFailedArtifact(githubArtifactFailedMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubFilesFetchFailedEvent,
    ) {
        ingestionStatusService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }

    @EventListener
    fun on(
        event: GithubFileDeletedEvent,
    ) {
        artifactIngestionService.unIngestFileArtifact(event)
    }
}
