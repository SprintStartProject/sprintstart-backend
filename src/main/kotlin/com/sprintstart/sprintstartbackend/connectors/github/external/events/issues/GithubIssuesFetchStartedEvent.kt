package com.sprintstart.sprintstartbackend.connectors.github.external.events.issues

import java.util.UUID

/**
 * Event emitted when the process of fetching GitHub issues for a specific repository starts.
 *
 * @property transactionId Unique identifier for the transaction associated with this fetching event.
 * @property repositoryOwner The owner of the GitHub repository for which issues are being fetched.
 * @property repositoryName The name of the GitHub repository for which issues are being fetched.
 */
data class GithubIssuesFetchStartedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
