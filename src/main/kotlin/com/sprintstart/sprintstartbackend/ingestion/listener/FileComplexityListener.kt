package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.FileAuthorshipService
import com.sprintstart.sprintstartbackend.ingestion.service.FileComplexityService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Measures the files the Git connectors read, so what is known about them can be used without reading
 * them again: how complicated they are and who wrote their lines.
 */
@Component
internal class FileComplexityListener(
    private val complexity: FileComplexityService,
    private val authorship: FileAuthorshipService,
) {
    @EventListener
    fun on(event: GithubFileFetchedEvent) {
        measure("github:${event.repositoryOwner}/${event.repositoryName}", event.path, event.content, event.authorLines)
    }

    @EventListener
    fun on(event: GithubFileDeletedEvent) {
        forget("github:${event.repositoryOwner}/${event.repositoryName}", event.path)
    }

    @EventListener
    fun on(event: BitbucketFileFetchedEvent) {
        measure("bitbucket:${event.workspace}/${event.slug}", event.path, event.content, event.authorLines)
    }

    @EventListener
    fun on(event: BitbucketFileDeletedEvent) {
        forget("bitbucket:${event.workspace}/${event.slug}", event.path)
    }

    private fun measure(repositoryKey: String, path: String, content: String, authorLines: Map<String, Int>) {
        complexity.measure(repositoryKey, path, content)
        authorship.record(repositoryKey, path, authorLines)
    }

    private fun forget(repositoryKey: String, path: String) {
        complexity.forget(repositoryKey, path)
        authorship.forget(repositoryKey, path)
    }
}
