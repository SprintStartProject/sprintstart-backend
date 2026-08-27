package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchResult
import java.util.UUID

/** Provides the canonical write boundary used by the Confluence connector. */
interface ConfluenceArtifactIngestionApi {
    fun startRun(runId: UUID, connectionId: UUID, sourceRef: String)

    fun persistBatch(command: ConfluenceArtifactBatchCommand): ConfluenceArtifactBatchResult

    fun finishRun(runId: UUID)

    fun failRun(runId: UUID, failureReason: String)
}
