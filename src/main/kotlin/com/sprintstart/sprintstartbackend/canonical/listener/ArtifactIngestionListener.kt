package com.sprintstart.sprintstartbackend.canonical.listener

import com.sprintstart.sprintstartbackend.github.external.events.GithubFileFetchedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class ArtifactIngestionListener(

) {
    @EventListener
    fun handleArtifact(
        event: GithubFileFetchedEvent,
    ) {

    }
}
