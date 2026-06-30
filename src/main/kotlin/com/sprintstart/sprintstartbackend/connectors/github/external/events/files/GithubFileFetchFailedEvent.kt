package com.sprintstart.sprintstartbackend.connectors.github.external.events.files

import java.util.UUID

/**
 * Emitted when a file fetch operation fails.
 *
 * @property transactionId A unique identifier for the transaction associated with this failure event.
 * @property repositoryOwner The owner of the repository where the file fetch attempt was made.
 * @property repositoryName The name of the repository where the file fetch attempt was made.
 * @property path The file path of the file that failed to be retrieved within the repository.
 * @property reason An explanation or message describing the cause of the fetch failure.
 */
data class GithubFileFetchFailedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val path: String,
    val reason: String,
)
