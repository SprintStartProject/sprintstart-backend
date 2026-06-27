package com.sprintstart.sprintstartbackend.github.external.events.pullrequests

import java.util.UUID

/**
 * Emitted when the process of fetching GitHub pull requests is started.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property repositoryOwner The owner of the GitHub repository whose pull requests are being fetched.
 * @property repositoryName The name of the GitHub repository whose pull requests are being fetched.
 */
data class GithubPullRequestsFetchStartedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
