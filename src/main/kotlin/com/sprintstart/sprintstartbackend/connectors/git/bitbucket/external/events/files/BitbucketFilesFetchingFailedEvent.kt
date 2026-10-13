package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * Announces that fetching the files of one connected Bitbucket repository failed.
 *
 * Covers the whole run failing — the clone could not be created, the remote could not be reached, or
 * the revision could not be read. A single unreadable file does not fail the run; it is reported on
 * its own as a [BitbucketFileFetchFailedEvent], so one bad file can never stop a repository from
 * making progress.
 *
 * @property transactionId The ingestion run this fetch belonged to.
 * @property repositoryId The connected repository that could not be fetched.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property reason A human-readable explanation of the failure.
 */
data class BitbucketFilesFetchingFailedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val reason: String,
)
