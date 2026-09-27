package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits

import java.util.UUID

/**
 * Announces that fetching the commits of one connected Bitbucket repository failed.
 *
 * @property transactionId The ingestion run this fetch belonged to.
 * @property repositoryId The connected repository that could not be fetched.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property reason A human-readable explanation of the failure.
 */
data class BitbucketCommitsFetchingFailedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val reason: String,
)
