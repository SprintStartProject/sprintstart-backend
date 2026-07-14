package com.sprintstart.sprintstartbackend.ingestion.listener.upload

import com.sprintstart.sprintstartbackend.ingestion.model.mapper.UploadArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.provider.UploadArtifactProviderService
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadFileDeletedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Handles per-artifact upload events that affect the ingestion artifact store.
 *
 * Batch lifecycle events decide when a run starts and finishes. This listener only handles the
 * individual artifact changes that happen inside the batch: a stored upload creates or updates an
 * ingestion artifact, and a deleted upload removes the matching ingestion artifact by source id.
 */
@Component
internal class UploadListener(
    private val uploadArtifactMapper: UploadArtifactMapper,
    private val uploadArtifactProviderService: UploadArtifactProviderService,
) {
    @EventListener
    fun on(
        event: ArtifactUploadedEvent,
    ) {
        uploadArtifactProviderService.persistArtifact(
            uploadArtifactMapper.toCommand(event),
        )
    }

    @EventListener
    fun on(
        event: UploadFileDeletedEvent,
    ) {
        uploadArtifactProviderService.deleteArtifact(
            transactionId = event.transactionId,
            uploadArtifactId = event.uploadArtifactId,
        )
    }
}
