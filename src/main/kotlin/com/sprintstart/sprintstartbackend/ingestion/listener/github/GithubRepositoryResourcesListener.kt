package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubRepositoryResourcesListener(
    private val artifactIngestionService: ArtifactIngestionService,
) {
    @EventListener
    fun on(
        event: GithubRepositoryResourcesFetchingStartedEvent,
    ) {
        artifactIngestionService
            .updateRunStatus(
                transactionId = event.transactionId,
                status = IngestionRunStatus.RUNNING,
            )
    }
}
