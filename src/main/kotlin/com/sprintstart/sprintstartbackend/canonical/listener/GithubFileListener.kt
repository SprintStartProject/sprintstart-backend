package com.sprintstart.sprintstartbackend.canonical.listener

import com.sprintstart.sprintstartbackend.canonical.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.canonical.model.mapper.GithubArtifactFailedMapper
import com.sprintstart.sprintstartbackend.canonical.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.canonical.service.GithubFetchingCompletionTracker

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchFailedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class GithubFileListener(

    private val artifactIngestionService: ArtifactIngestionService,
    private val gitHubFetchingCompletionTracker: GithubFetchingCompletionTracker,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubArtifactFailedMapper: GithubArtifactFailedMapper
) {

    @ApplicationModuleListener
    fun on(
        event: GithubCommitsFetchStartedEvent,
    ) {

    }

    @ApplicationModuleListener
    fun on(
        event: GithubFileFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: GithubFilesFetchCompletedEvent,
    ) {
        gitHubFetchingCompletionTracker.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }

    @ApplicationModuleListener
    fun on(
        event: GithubFileFetchFailedEvent,
    ) {
        artifactIngestionService.addFailedArtifact(githubArtifactFailedMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: GithubFilesFetchFailedEvent,
    ) {
        gitHubFetchingCompletionTracker.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }





}
