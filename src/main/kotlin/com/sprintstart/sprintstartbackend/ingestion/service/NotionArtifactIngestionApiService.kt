package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.NotionArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import org.springframework.stereotype.Service
import java.util.UUID

/** Implements the Notion connector's canonical artifact write boundary. */
@Service
internal class NotionArtifactIngestionApiService(
    private val runLifeCycleService: IngestionRunLifeCycleService,
    private val itemPersistenceService: NotionArtifactItemPersistenceService,
    private val artifactProjectService: ArtifactProjectService,
) : NotionArtifactIngestionApi {
    override fun startRun(runId: UUID, connectionId: UUID, sourceRef: String) {
        runLifeCycleService.startOrUpdateRun(
            transactionId = runId,
            sourceSystem = SourceSystem.NOTION,
            status = IngestionRunStatus.RUNNING,
            sourceInstanceId = connectionId,
            sourceInstanceRef = sourceRef,
        )
    }

    override fun persistPage(
        runId: UUID,
        projectId: UUID,
        command: NotionPageArtifactCommand,
    ): NotionArtifactWriteResult {
        return itemPersistenceService.persist(runId, projectId, command)
    }

    override fun finishRun(runId: UUID, successfulItemCount: Int) {
        runLifeCycleService.finishRun(runId, successfulItemCount)
    }

    override fun recordPageFailure(runId: UUID, sourceId: String, sourceUrl: String?, reason: String) {
        itemPersistenceService.recordFailure(runId, sourceId, sourceUrl, reason)
    }

    override suspend fun unlinkPage(runId: UUID, projectId: UUID, connectionId: UUID, pageId: String) {
        artifactProjectService.applyProjectLink(ArtifactSourceRef.NotionPage(connectionId, pageId), projectId, false)
        itemPersistenceService.recordUnlinked(runId)
    }

    override fun failRun(runId: UUID, failureReason: String) {
        runLifeCycleService.startOrUpdateRun(
            transactionId = runId,
            sourceSystem = SourceSystem.NOTION,
            status = IngestionRunStatus.FAILED,
            failureReason = failureReason,
        )
    }
}
