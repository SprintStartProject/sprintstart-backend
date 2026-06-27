package com.sprintstart.sprintstartbackend.github.external.events.initial

import java.util.UUID

/**
 * Emitted when the initiation of a connection to a GitHub repository is started.
 *
 * @property transactionId A unique identifier representing the transaction associated with this event.
 * @property owner The owner of the GitHub repository for which the connection is being initiated.
 * @property name The name of the GitHub repository for which the connection is being initiated.
 */
data class GithubRepositoryConnectionInitiatedEvent(
    val transactionId: UUID,
    val owner: String,
    val name: String,
)
