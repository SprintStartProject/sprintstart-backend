package com.sprintstart.sprintstartbackend.ingestion.listener.upload

import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunLifeCycleService
import com.sprintstart.sprintstartbackend.ingestion.service.UploadIngestionRunService
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchDeletionFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Bridges upload lifecycle events into ingestion run lifecycle updates.
 *
 * The upload module owns file storage and publishes coarse batch events. This listener creates the
 * ingestion run when a batch starts and delegates upload or deletion completion to the service that
 * records failed outcomes and finalizes the run.
 */
@Component
internal class UploadIngestionRunLifecycleListener(
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
    private val uploadIngestionRunService: UploadIngestionRunService,
) {
    @EventListener
    fun on(
        event: UploadStartedEvent,
    ) {
        ingestionRunLifeCycleService
            .startRun(
                transactionId = event.transactionId,
                sourceSystem = SourceSystem.UPLOAD,
                status = IngestionRunStatus.RUNNING,
            )
    }

    @EventListener
    fun on(
        event: UploadBatchFinishedEvent,
    ) {
        uploadIngestionRunService.finishUploadIngestionRun(event)
    }

    @EventListener
    fun on(
        event: UploadBatchDeletionFinishedEvent,
    ) {
        uploadIngestionRunService.finishUploadDeletionIngestionRun(event)
    }
}
