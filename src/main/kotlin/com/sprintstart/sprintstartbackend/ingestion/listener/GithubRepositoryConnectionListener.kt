package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactIngestionService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubRepositoryConnectionListener(
    private val artifactIngestionService: ArtifactIngestionService,
) {
    @EventListener
    fun on(
        event: GithubRepositoryConnectionInitiatedEvent,
    ) {
        artifactIngestionService
            .startRun(
                transactionId = event.transactionId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.CONNECTED,
            )
    }

    @EventListener
    fun on(
        event: GithubRepositoryConnectionInitiationFailedEvent,
    ) {
        artifactIngestionService
            .startRun(
                transactionId = event.transactionId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
                failureReason = event.reason,
            )
    }
}
