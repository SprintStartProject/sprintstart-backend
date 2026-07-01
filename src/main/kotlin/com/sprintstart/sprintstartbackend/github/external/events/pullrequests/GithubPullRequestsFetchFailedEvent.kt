package com.sprintstart.sprintstartbackend.github.external.events.pullrequests

import java.util.UUID

/**
 * Emitted when the process of fetching GitHub pull requests fails.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property repositoryOwner The owner of the GitHub repository whose pull requests failed to be fetched.
 * @property repositoryName The name of the GitHub repository whose pull requests failed to be fetched.
 * @property reason The reason for the fetch failure, defaulting to "Internal server error" if not provided.
 */
data class GithubPullRequestsFetchFailedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val reason: String = "Internal server error",
)
