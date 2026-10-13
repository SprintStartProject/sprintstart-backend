package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesResyncedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.FailedArtifactService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class BitbucketFileListener(
    private val bitbucketArtifactProviderService: BitbucketArtifactProviderService,
    private val bitbucketArtifactMapper: BitbucketArtifactMapper,
    private val bitbucketArtifactFailedMapper: BitbucketArtifactFailedMapper,
    private val bitbucketIngestionRunService: BitbucketIngestionRunService,
    private val failedArtifactService: FailedArtifactService,
) {
    @EventListener
    fun on(event: BitbucketFileFetchedEvent) {
        bitbucketArtifactProviderService.persistArtifact(bitbucketArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(event: BitbucketFilesFetchingCompletedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFinished(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
        )
    }

    @EventListener
    fun on(event: BitbucketFileFetchFailedEvent) {
        failedArtifactService.addFailedArtifact(bitbucketArtifactFailedMapper.toCommand(event))
    }

    @EventListener
    fun on(event: BitbucketFilesResyncedEvent) {
        bitbucketArtifactProviderService.reconcileDeletedFiles(event)
    }

    @EventListener
    fun on(event: BitbucketFilesFetchingFailedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFailed(
            event.transactionId,
            finishedType = FinishedTypes.FILES,
            reason = event.reason,
        )
    }
}
