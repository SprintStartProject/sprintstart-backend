package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class BitbucketPullRequestListener(
    private val bitbucketArtifactProviderService: BitbucketArtifactProviderService,
    private val bitbucketArtifactMapper: BitbucketArtifactMapper,
    private val bitbucketIngestionRunService: BitbucketIngestionRunService,
) {
    @EventListener
    fun on(event: BitbucketPullRequestFetchedEvent) {
        bitbucketArtifactProviderService.persistArtifact(bitbucketArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(event: BitbucketPullRequestsFetchingCompletedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.PULL_REQUESTS,
        )
    }

    @EventListener
    fun on(event: BitbucketPullRequestsFetchingFailedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFailed(
            event.transactionId,
            finishedType = FinishedTypes.PULL_REQUESTS,
            reason = event.reason ?: "Unknown error",
        )
    }
}
