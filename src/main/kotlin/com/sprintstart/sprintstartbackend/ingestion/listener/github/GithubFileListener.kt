package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.FailedArtifactService
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubFileListener(
    private val githubArtifactProviderService: GithubArtifactProviderService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubArtifactFailedMapper: GithubArtifactFailedMapper,
    private val githubIngestionRunService: GithubIngestionRunService,
    private val failedArtifactService: FailedArtifactService,
) {
    @EventListener
    fun on(
        event: GithubFileFetchedEvent,
    ) {
        githubArtifactProviderService.persistArtifact(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubFilesFetchCompletedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }

    @EventListener
    fun on(
        event: GithubFileFetchFailedEvent,
    ) {
        failedArtifactService.addFailedArtifact(githubArtifactFailedMapper.toCommand(event))
    }

    @EventListener
    fun on(
        event: GithubFilesFetchFailedEvent,
    ) {
        githubIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }

    @EventListener
    fun on(
        event: GithubFileDeletedEvent,
    ) {
        githubArtifactProviderService.deleteFileArtifact(event)
    }
}
