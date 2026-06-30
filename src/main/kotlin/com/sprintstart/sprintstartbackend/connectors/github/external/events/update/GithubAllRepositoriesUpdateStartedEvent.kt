package com.sprintstart.sprintstartbackend.connectors.github.external.events.update

import java.util.UUID

/**
 * Emitted when the update of all GitHub repositories has started.
 *
 * @property transactionId Unique identifier for the update transaction, used for tracking
 * updates and logging.
 */
data class GithubAllRepositoriesUpdateStartedEvent(
    val transactionId: UUID,
)
