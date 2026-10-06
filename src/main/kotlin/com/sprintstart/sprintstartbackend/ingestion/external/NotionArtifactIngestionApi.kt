package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import java.util.UUID

/**
 * Provides the canonical artifact and run-history boundary used by the Notion connector.
 *
 * The connector supplies credential-free page content and stable source IDs. The ingestion module
 * owns artifact persistence, project membership, indexing work queues, and ingestion-run state.
 */
interface NotionArtifactIngestionApi {
    /** Opens one run for a workspace connection before any page is processed. */
    fun startRun(runId: UUID, connectionId: UUID, sourceRef: String)

    /** Upserts one canonical page artifact and returns whether it was created, updated, or unchanged. */
    fun persistPage(
        runId: UUID,
        projectId: UUID,
        command: NotionPageArtifactCommand,
    ): NotionArtifactWriteResult

    /** Completes a run after all discovered and removed pages have been reconciled. */
    fun finishRun(runId: UUID, successfulItemCount: Int)

    /** Marks a run as terminally failed when connection-wide work could not start or finish. */
    fun failRun(runId: UUID, failureReason: String)

    /** Records one isolated page failure without aborting the remaining workspace run. */
    fun recordPageFailure(runId: UUID, sourceId: String, sourceUrl: String?, reason: String)

    /** Removes one page artifact and its indexed chunks from a project while retaining canonical data. */
    suspend fun unlinkPage(runId: UUID, projectId: UUID, connectionId: UUID, pageId: String)
}
