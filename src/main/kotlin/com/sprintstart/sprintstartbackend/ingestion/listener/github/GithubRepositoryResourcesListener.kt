package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class GithubRepositoryResourcesListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    @EventListener
    fun on(
        event: GithubRepositoryResourcesFetchingStartedEvent,
    ) {
        ingestionRunLifeCycleService
            .updateRunStatus(
                transactionId = event.transactionId,
                status = IngestionRunStatus.RUNNING,
            )
    }
}
