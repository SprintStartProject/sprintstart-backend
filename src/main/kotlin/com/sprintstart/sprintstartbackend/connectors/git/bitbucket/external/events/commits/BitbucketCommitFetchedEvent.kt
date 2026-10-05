package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits

import java.time.Instant
import java.util.UUID

/**
 * One commit of a Bitbucket repository that was read from its clone.
 *
 * @property transactionId The ingestion run this commit belongs to.
 * @property repositoryId The connected repository the commit was read from.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property author The Git author name recorded in the commit. This is free text chosen by the
 *           committer, not a Bitbucket account handle, and must not be used to attribute the commit
 *           to an account.
 * @property committedAt The commit time.
 * @property sha The full commit SHA.
 * @property subject The first line of the commit message.
 * @property sourceUrl A link to the commit on Bitbucket.
 */
data class BitbucketCommitFetchedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val author: String,
    val committedAt: Instant,
    val sha: String,
    val subject: String,
    val sourceUrl: String,
)
