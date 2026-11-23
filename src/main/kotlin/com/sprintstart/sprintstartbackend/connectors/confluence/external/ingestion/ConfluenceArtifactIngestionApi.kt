package com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion

import java.util.UUID

/**
 * The canonical write boundary the Confluence connector hands its pages to.
 *
 * The connector owns this interface and the ingestion module implements it, so the dependency
 * points from ingestion to the connector, the same way as for the connectors that publish events.
 */
interface ConfluenceArtifactIngestionApi {
    fun startRun(runId: UUID, connectionId: UUID, sourceRef: String)

    fun persistBatch(command: ConfluenceArtifactBatchCommand): ConfluenceArtifactBatchResult

    fun finishRun(runId: UUID, successfulItemCount: Int)

    fun failRun(runId: UUID, failureReason: String)
}
