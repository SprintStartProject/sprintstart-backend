package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubRepositoryConnectionListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    @EventListener
    fun on(
        event: GithubRepositoryConnectionInitiatedEvent,
    ) {
        ingestionRunLifeCycleService
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
        ingestionRunLifeCycleService
            .startRun(
                transactionId = event.transactionId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.FAILED,
                failureReason = event.reason,
            )
    }
}
