package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Persists fetched Bitbucket commits and tracks the commit phase of their ingestion run.
 *
 * Bitbucket commits arrive one event per commit, sequentially from the shared engine, so each event
 * is mapped and persisted on its own rather than being collected first: the run's counters move as
 * the commits arrive instead of spiking at the end.
 */
@Component
internal class BitbucketCommitListener(
    private val bitbucketArtifactProviderService: BitbucketArtifactProviderService,
    private val bitbucketArtifactMapper: BitbucketArtifactMapper,
    private val bitbucketIngestionRunService: BitbucketIngestionRunService,
) {
    @EventListener
    fun on(event: BitbucketCommitFetchedEvent) {
        bitbucketArtifactProviderService.persistArtifact(bitbucketArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(event: BitbucketCommitsFetchingCompletedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.COMMITS,
        )
    }

    @EventListener
    fun on(event: BitbucketCommitsFetchingFailedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFailed(
            event.transactionId,
            finishedType = FinishedTypes.COMMITS,
            reason = event.reason,
        )
    }
}
