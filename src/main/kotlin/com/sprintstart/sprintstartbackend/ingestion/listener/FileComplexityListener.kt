package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.FileComplexityService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Measures the files the Git connectors read, so their complexity can be used without reading them again. */
@Component
internal class FileComplexityListener(
    private val complexity: FileComplexityService,
) {
    @EventListener
    fun on(event: GithubFileFetchedEvent) {
        complexity.measure("github:${event.repositoryOwner}/${event.repositoryName}", event.path, event.content)
    }

    @EventListener
    fun on(event: GithubFileDeletedEvent) {
        complexity.forget("github:${event.repositoryOwner}/${event.repositoryName}", event.path)
    }

    @EventListener
    fun on(event: BitbucketFileFetchedEvent) {
        complexity.measure("bitbucket:${event.workspace}/${event.slug}", event.path, event.content)
    }
}
