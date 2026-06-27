package com.sprintstart.sprintstartbackend.github.external.events.files

import java.util.UUID

/**
 * Event triggered when a file is deleted from a GitHub repository.
 *
 * This event represents the occurrence of a file deletion action within a repository.
 * It includes a unique transaction identifier for tracking the deletion operation and
 * the path of the file that was deleted.
 *
 * @property transactionId A unique identifier for the transaction associated with this deletion event.
 * @property repositoryOwner The owner of the repository where the file was deleted.
 * @property repositoryName The name of the repository where the file was deleted.
 * @property path The file path of the deleted file within the repository.
 */
data class GithubFileDeletedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val path: String,
)
