package com.sprintstart.sprintstartbackend.canonical.listener

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactIngestionService
import com.sprintstart.sprintstartbackend.github.external.events.GithubRepositoryResourcesFetchingStartedEvent

import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class GithubRepositoryResourcesListener(

    private val artifactIngestionService: ArtifactIngestionService,
) {

    @ApplicationModuleListener
    fun on(
        event: GithubRepositoryResourcesFetchingStartedEvent,
    ) {
        artifactIngestionService
            .updateRunStatus(
                transactionId = event.transactionId,
                status = IngestionRunStatus.RUNNING
            )
    }
}
