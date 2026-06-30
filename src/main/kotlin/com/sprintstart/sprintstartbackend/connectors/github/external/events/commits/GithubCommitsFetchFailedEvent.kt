package com.sprintstart.sprintstartbackend.connectors.github.external.events.commits

import java.util.UUID

/**
 * Emitted when the fetching of commits from a GitHub repository fails.
 *
 * @property transactionId Unique identifier for the transaction associated with the failed operation.
 * @property repositoryOwner The owner of the GitHub repository where commits were being fetched.
 * @property repositoryName The name of the GitHub repository where commits were being fetched.
 * @property reason A description of the reason for the failure. Defaults to "Internal server error".
 */
data class GithubCommitsFetchFailedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val reason: String = "Internal server error",
)
