package com.sprintstart.sprintstartbackend.github.external.events.update

import java.util.UUID

/**
 * Emitted when the update of a GitHub repository fails.
 *
 * @property transactionId Unique identifier for the update transaction, used for tracking
 * updates and debugging.
 * @property owner The GitHub username or organization name of the repository owner.
 * @property name The name of the GitHub repository that failed to update.
 * @property reason A message describing the reason for the update failure. Defaults to "Internal server error".
 */
data class GithubRepositoryUpdateFailedEvent(
    val transactionId: UUID,
    val owner: String,
    val name: String,
    val reason: String = "Internal server error",
)
