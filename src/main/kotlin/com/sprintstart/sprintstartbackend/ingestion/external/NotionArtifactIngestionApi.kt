package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import java.util.UUID

/** Provides the canonical artifact write boundary used by the Notion connector. */
interface NotionArtifactIngestionApi {
    fun startRun(runId: UUID, connectionId: UUID, sourceRef: String)

    fun persistPage(
        runId: UUID,
        projectId: UUID,
        command: NotionPageArtifactCommand,
    ): NotionArtifactWriteResult

    fun finishRun(runId: UUID, successfulItemCount: Int)

    fun failRun(runId: UUID, failureReason: String)
}
