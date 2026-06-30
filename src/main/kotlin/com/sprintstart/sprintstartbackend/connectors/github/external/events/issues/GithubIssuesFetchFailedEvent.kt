package com.sprintstart.sprintstartbackend.connectors.github.external.events.issues

import java.util.UUID

/**
 * Emitted when the fetching of GitHub issues from a specific repository fails.
 *
 * @property transactionId Unique identifier for the transaction associated with the failed fetch operation.
 * @property repositoryOwner The owner of the GitHub repository for which the fetch attempt failed.
 * @property repositoryName The name of the GitHub repository for which the fetch attempt failed.
 * @property reason A descriptive message providing the reason for the failure. Defaults to "Internal server error".
 */
data class GithubIssuesFetchFailedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val reason: String = "Internal server error",
)
