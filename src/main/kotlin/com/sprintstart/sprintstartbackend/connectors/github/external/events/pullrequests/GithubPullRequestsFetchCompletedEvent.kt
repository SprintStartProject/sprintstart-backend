package com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests

import java.util.UUID

/**
 * Emitted when the process of fetching GitHub pull requests is completed.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property repositoryOwner The owner of the GitHub repository whose pull requests were fetched.
 * @property repositoryName The name of the GitHub repository whose pull requests were fetched.
 */
data class GithubPullRequestsFetchCompletedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
