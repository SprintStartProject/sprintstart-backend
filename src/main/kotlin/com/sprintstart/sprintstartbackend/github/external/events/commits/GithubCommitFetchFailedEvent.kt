package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

/**
 * Emitted when a commit fetch operation fails.
 *
 * @property transactionId Unique identifier for the transaction associated with the failed operation.
 * @property repositoryOwner The owner of the GitHub repository where the commit fetch was attempted.
 * @property repositoryName The name of the repository where the commit fetch was attempted.
 * @property sha The unique identifier (SHA) of the commit that failed to fetch, if available.
 * @property reason A description of the failure reason.
 */
data class GithubCommitFetchFailedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val sha: String?,
    val reason: String,
)
