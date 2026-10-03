package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * Announces that the files of one connected Bitbucket repository were fetched successfully.
 *
 * Published once per run, so a listener can mark the file phase of the run as finished even when no
 * file changed since the previous run.
 *
 * @property transactionId The ingestion run this fetch belonged to.
 * @property repositoryId The connected repository that was fetched.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 */
data class BitbucketFilesFetchingCompletedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
)
