package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files

import java.util.UUID

/**
 * One file of a Bitbucket repository that belongs to the ingested revision but could not be read.
 *
 * Reported per file so that a file which was skipped stays distinguishable from one the repository
 * does not contain. The run itself still completes and still advances its revision cursor, so a
 * single file that is too large or not valid UTF-8 cannot leave the repository permanently stuck on
 * an old revision.
 *
 * @property transactionId The ingestion run this file belongs to.
 * @property repositoryId The connected repository the file belongs to.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property path The repository-relative path of the file that failed.
 * @property reason A human-readable explanation of the failure.
 */
data class BitbucketFileFetchFailedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val path: String,
    val reason: String,
)
