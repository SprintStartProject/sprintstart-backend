package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactBatchResult
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
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

    override fun finishRun(runId: UUID, successfulItemCount: Int) {
        runLifeCycleService.finishRun(runId, successfulItemCount)
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
