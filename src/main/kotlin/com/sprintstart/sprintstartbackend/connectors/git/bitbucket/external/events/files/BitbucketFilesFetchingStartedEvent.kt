package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * Announces that the files of one connected Bitbucket repository are being fetched.
 *
 * @property transactionId The ingestion run this fetch reports its progress under.
 * @property repositoryId The connected repository being fetched.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 */
data class BitbucketFilesFetchingStartedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
)
