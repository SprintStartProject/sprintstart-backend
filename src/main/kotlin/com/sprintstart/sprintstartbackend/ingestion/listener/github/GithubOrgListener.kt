package com.sprintstart.sprintstartbackend.ingestion.listener.github

import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.GithubIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.GithubArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class GithubOrgListener(
    private val githubArtifactProviderService: GithubArtifactProviderService,
    private val githubArtifactMapper: GithubArtifactMapper,
    private val githubIngestionRunService: GithubIngestionRunService,
) {
    @EventListener
    fun on(event: GithubOrgMetadataFetchedEvent) {
        githubArtifactProviderService.persistArtifact(githubArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(event: GithubOrgMetadataFetchingFailedEvent) {
        githubIngestionRunService.markFetchPhaseFinished(event.transactionId, FinishedTypes.ORG_METADATA)
    }

    @EventListener
    fun on(event: GithubOrgMetadataFetchingCompletedEvent) {
        githubIngestionRunService.markFetchPhaseFinished(event.transactionId, FinishedTypes.ORG_METADATA)
    }
}
