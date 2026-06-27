package com.sprintstart.sprintstartbackend.canonical.listener

import com.sprintstart.sprintstartbackend.canonical.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.canonical.service.GithubFetchingCompletionTracker

import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssuesFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssuesFetchStartedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class GithubIssueListener(

    private val artifactIngestionService: ArtifactIngestionService,
    private val gitHubFetchingCompletionTracker: GithubFetchingCompletionTracker,
    private val githubArtifactMapper: GithubArtifactMapper
) {

    @ApplicationModuleListener
    fun on(
        event: GithubIssuesFetchStartedEvent,
    ) {

    }

    @ApplicationModuleListener
    fun on(
        event: GithubIssueFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: GithubIssuesFetchCompletedEvent,
    ) {
        gitHubFetchingCompletionTracker.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.ISSUES
        )
    }

    @ApplicationModuleListener
    fun on(
        event: GithubIssuesFetchFailedEvent,
    ) {
        gitHubFetchingCompletionTracker.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.ISSUES
        )
    }





}
