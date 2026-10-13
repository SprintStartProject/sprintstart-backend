package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Persists workspace metadata and closes the workspace phase of a Bitbucket ingestion run.
 *
 * Mirrors [com.sprintstart.sprintstartbackend.ingestion.listener.github.GithubOrgListener], with
 * one deliberate difference: a failed workspace fetch closes its phase through the failed path, so
 * the failure is recorded on the run and turns it `PARTIAL` or `FAILED` instead of letting a
 * workspace that could not be read look like one that legitimately reported nothing.
 *
 * The started event needs no handler: the run is already open by the time the workspace fetch
 * reports, because the connection and update events open it before any collector starts.
 */
@Component
internal class BitbucketWorkspaceListener(
    private val bitbucketArtifactProviderService: BitbucketArtifactProviderService,
    private val bitbucketArtifactMapper: BitbucketArtifactMapper,
    private val bitbucketIngestionRunService: BitbucketIngestionRunService,
) {
    @EventListener
    fun on(event: BitbucketWorkspaceMetadataFetchedEvent) {
        bitbucketArtifactProviderService.persistArtifact(bitbucketArtifactMapper.toCommand(event))
    }

    @EventListener
    fun on(event: BitbucketWorkspaceMetadataFetchingFailedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFailed(
            event.transactionId,
            finishedType = FinishedTypes.ORG_METADATA,
            reason = event.reason ?: "Unknown error",
        )
    }

    @EventListener
    fun on(event: BitbucketWorkspaceMetadataFetchingCompletedEvent) {
        bitbucketIngestionRunService.markFetchPhaseFinished(event.transactionId, FinishedTypes.ORG_METADATA)
    }
}
