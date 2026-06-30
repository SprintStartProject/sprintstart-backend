package com.sprintstart.sprintstartbackend.connectors.github.external.events.issues

import java.util.UUID

/**
 * Emitted when the fetching of GitHub issues for a specific repository has completed successfully.
 *
 * This event serves as a notification that the process of retrieving issues from a GitHub repository
 * has been successfully completed. It contains essential details to identify the transaction and
 * specify the repository information associated with the fetching process.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property repositoryOwner The owner of the GitHub repository for which issues were fetched.
 * @property repositoryName The name of the GitHub repository for which issues were fetched.
 */
data class GithubIssuesFetchCompletedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
