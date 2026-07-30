package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
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
            .startOrUpdateRun(
                transactionId = event.transactionId,
                sourceSystem = SourceSystem.GITHUB,
                status = IngestionRunStatus.RUNNING,
                sourceInstanceId = event.repositoryId,
                sourceInstanceRef = "${event.owner}/${event.name}",
            )
    }
}
