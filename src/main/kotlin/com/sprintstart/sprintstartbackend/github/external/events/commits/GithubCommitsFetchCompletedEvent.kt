package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

/**
 * Emitted when the fetching of commits from a GitHub repository is completed.
 *
 * @property transactionId Unique identifier for the transaction associated with the fetching operation.
 * @property repositoryOwner The owner of the GitHub repository from which commits were fetched.
 * @property repositoryName The name of the GitHub repository from which commits were fetched.
 */
data class GithubCommitsFetchCompletedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
