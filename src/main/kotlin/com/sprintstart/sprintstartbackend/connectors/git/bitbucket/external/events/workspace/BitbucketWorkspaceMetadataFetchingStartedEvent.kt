package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace

import java.util.UUID

/**
 * Announces that the metadata of one Bitbucket workspace is about to be fetched.
 *
 * @property transactionId The ingestion run this fetch reports under.
 */
data class BitbucketWorkspaceMetadataFetchingStartedEvent(
    val transactionId: UUID,
)
