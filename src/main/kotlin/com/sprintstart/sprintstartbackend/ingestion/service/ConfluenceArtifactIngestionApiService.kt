package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ConfluenceArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class ConfluenceArtifactIngestionApiService(
    private val runLifeCycleService: IngestionRunLifeCycleService,
    private val batchService: ConfluenceArtifactBatchService,
) : ConfluenceArtifactIngestionApi {
    override fun startRun(runId: UUID, connectionId: UUID, sourceRef: String) {
        runLifeCycleService.startOrUpdateRun(
            transactionId = runId,
            sourceSystem = SourceSystem.CONFLUENCE,
            status = IngestionRunStatus.RUNNING,
            sourceInstanceId = connectionId,
            sourceInstanceRef = sourceRef,
        )
    }

    override fun persistBatch(command: ConfluenceArtifactBatchCommand): ConfluenceArtifactBatchResult {
        return batchService.persist(command)
    }

    override fun finishRun(runId: UUID) {
        runLifeCycleService.finishRun(runId)
    }

    override fun failRun(runId: UUID, failureReason: String) {
        runLifeCycleService.startOrUpdateRun(
            transactionId = runId,
            sourceSystem = SourceSystem.CONFLUENCE,
            status = IngestionRunStatus.FAILED,
            failureReason = failureReason,
        )
    }
}
