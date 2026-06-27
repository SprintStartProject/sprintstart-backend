package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

/**
 * Emitted when the fetching of commits from a GitHub repository is started.
 *
 * @property transactionId Unique identifier for the transaction associated with the fetching operation.
 * @property repositoryOwner The owner of the GitHub repository from which commits are being fetched.
 * @property repositoryName The name of the GitHub repository from which commits are being fetched.
 */
data class GithubCommitsFetchStartedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
