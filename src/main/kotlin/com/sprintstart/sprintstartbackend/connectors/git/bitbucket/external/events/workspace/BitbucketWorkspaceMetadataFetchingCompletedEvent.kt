package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace

import java.util.UUID

/**
 * Closes the workspace-metadata fetch of one Bitbucket workspace.
 *
 * Published also when the fetch was skipped because the workspace's metadata had already been
 * fetched before, so the ingestion run can mark the workspace phase as finished either way.
 *
 * @property transactionId The ingestion run this fetch belongs to.
 */
data class BitbucketWorkspaceMetadataFetchingCompletedEvent(
    val transactionId: UUID,
)
