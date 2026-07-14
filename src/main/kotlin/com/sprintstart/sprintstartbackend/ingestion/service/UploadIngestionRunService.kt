package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.UploadArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactStatus
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchDeletionFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchFinishedEvent
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * Finalizes ingestion runs that originate from the upload module.
 *
 * Upload batches report per-artifact outcomes after the storage operation has completed. This
 * service turns failed outcomes into ingestion failure records and then delegates the final run
 * status calculation to the shared lifecycle service. The same flow is used for upload creation
 * and upload deletion batches.
 */
@Service
class UploadIngestionRunService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val failedArtifactService: FailedArtifactService,
    private val uploadArtifactFailedMapper: UploadArtifactFailedMapper,
    private val ingestionRunLifeCycleService: IngestionRunLifeCycleService,
) {
    /**
     * Failed artifact outcomes are stored before the run is marked completed, partial, or failed,
     * so the final status reflects both successful artifact writes and per-artifact failures.
     *
     * Non-failed outcomes are ignored here because upload artifact listeners have already applied
     * their storage-side effects and run counters.
     *
     * @param event The upload batch completion event for the ingestion run.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun finishUploadIngestionRun(event: UploadBatchFinishedEvent) {
        val run = ingestionRunRepository
            .findByIdForUpdate(event.transactionId)
            .orElseThrow { IngestionRunNotFoundException(event.transactionId) }
        val outcomes = event.uploadArtifactOperationOutcomes
        outcomes.forEach {
            when (it.status) {
                UploadArtifactStatus.FAILED,
                -> {
                    failedArtifactService.addFailedArtifact(
                        uploadArtifactFailedMapper.toCommand(
                            event = event,
                            outcome = it,
                            operation = "upload",
                        ),
                    )
                }

                else -> {}
            }
        }
        ingestionRunLifeCycleService.finishRun(run)
    }

    /**
     * Failed delete outcomes are tracked as failed artifacts before the run status is finalized.
     * Successful deletions are counted and deindexed by upload artifact listeners while each
     * artifact deletion event is handled.
     *
     * @param event The upload deletion batch completion event for the ingestion run.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun finishUploadDeletionIngestionRun(event: UploadBatchDeletionFinishedEvent) {
        val run = ingestionRunRepository
            .findByIdForUpdate(event.transactionId)
            .orElseThrow { IngestionRunNotFoundException(event.transactionId) }
        event.deleteArtifactOutcomes.forEach { outcome ->
            when (outcome.status) {
                UploadArtifactStatus.FAILED -> {
                    failedArtifactService.addFailedArtifact(
                        uploadArtifactFailedMapper.toCommand(
                            event = event,
                            outcome = outcome,
                            operation = "deletion",
                        ),
                    )
                }

                else -> {}
            }
        }
        ingestionRunLifeCycleService.finishRun(run)
    }
}
