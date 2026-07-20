package com.sprintstart.sprintstartbackend.connectors.github.external.events.update

import java.util.UUID

/**
 * Emitted when the update of a specific GitHub repository has started.
 *
 * @property transactionId Unique identifier for the update transaction, used for tracking
 * the progress and status of the update.
 * @property owner The GitHub username or organization name of the repository owner.
 * @property name The name of the GitHub repository being updated.
 */
data class GithubRepositoryUpdateStartedEvent(
    val transactionId: UUID,
    val owner: String,
    val name: String,
)
