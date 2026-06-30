package com.sprintstart.sprintstartbackend.connectors.github.external.events.initial

import java.util.UUID

/**
 * Emitted when the initiation of a connection to a GitHub repository fails.
 *
 * @property transactionId A unique identifier representing the transaction associated with this event.
 * @property owner The owner of the GitHub repository for which the connection initiation has failed.
 * @property name The name of the GitHub repository for which the connection initiation has failed.
 * @property reason An optional reason describing why the connection initiation has failed.
 */
data class GithubRepositoryConnectionInitiationFailedEvent(
    val transactionId: UUID,
    val owner: String,
    val name: String,
    val reason: String?,
)
