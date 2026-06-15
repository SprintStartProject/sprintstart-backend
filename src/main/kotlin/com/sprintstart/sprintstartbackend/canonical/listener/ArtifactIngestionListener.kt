package com.sprintstart.sprintstartbackend.canonical.listener

import com.sprintstart.sprintstartbackend.canonical.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.github.external.events.CommitsSyncStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.FilesSyncStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.IssuesSyncJobStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.PullRequestsSyncStartedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class ArtifactIngestionListener(
    private val githubArtifactMapper : GithubArtifactMapper,
    private val artifactIngestionService : ArtifactIngestionService
) {
    @ApplicationModuleListener
    fun on(
        event: GithubCommitFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: GithubFileFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: GithubIssueFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: GithubPullRequestFetchedEvent,
    ) {
        artifactIngestionService.ingest(githubArtifactMapper.toCommand(event))
    }

    @ApplicationModuleListener
    fun on(
        event: FilesSyncStartedEvent,
    ) {
        artifactIngestionService.startRun(
            transactionId = event.transactionId,
            expectedArtifacts = event.paths.size,
        )
    }

    @ApplicationModuleListener
    fun on(
        event: CommitsSyncStartedEvent,
    ) {
        artifactIngestionService.startRun(
            transactionId = event.transactionId,
            expectedArtifacts = event.shas.size,
        )
    }

    @ApplicationModuleListener
    fun on(
        event: IssuesSyncJobStartedEvent,
    ) {
        artifactIngestionService.startRun(
            transactionId = event.transactionId,
            expectedArtifacts = event.numbers.size,
        )
    }

    @ApplicationModuleListener
    fun on(
        event: PullRequestsSyncStartedEvent,
    ) {
        artifactIngestionService.startRun(
            transactionId = event.transactionId,
            expectedArtifacts = event.numbers.size,
        )
    }

}
