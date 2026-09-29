package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace

import java.util.UUID

/**
 * Reports that the workspace-metadata fetch of one Bitbucket workspace failed.
 *
 * A failure must not look like a clean completion: the ingestion side records the failure so the
 * run turns `PARTIAL` or `FAILED` instead of `COMPLETED` without workspace metadata.
 *
 * @property transactionId The ingestion run this fetch belongs to.
 * @property reason What the fetch failed with.
 */
data class BitbucketWorkspaceMetadataFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String?,
)
