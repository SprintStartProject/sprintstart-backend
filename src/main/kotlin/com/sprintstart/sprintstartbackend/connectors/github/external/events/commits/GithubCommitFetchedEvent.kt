package com.sprintstart.sprintstartbackend.connectors.github.external.events.commits

import java.time.Instant
import java.util.UUID

/**
 * Represents an event triggered when a GitHub commit is fetched.
 *
 * The event contains detailed metadata about the commit, including its unique identifier (SHA),
 * author, commit message, and date of creation. It is used within the system to process or
 * propagate commit-related information during synchronization workflows.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property repositoryOwner The owner of the GitHub repository where the commit was fetched.
 * @property repositoryName The name of the GitHub repository where the commit was fetched.
 * @property author The author of the commit.
 * @property date The timestamp indicating when the commit was created.
 * @property sha The unique identifier (SHA) of the commit.
 * @property msg The commit message provided at the time of the commit.
 */
data class GithubCommitFetchedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val author: String,
    val date: Instant,
    val sha: String,
    val msg: String,
)
