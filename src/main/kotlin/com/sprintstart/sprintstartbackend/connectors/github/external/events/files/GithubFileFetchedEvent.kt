package com.sprintstart.sprintstartbackend.connectors.github.external.events.files

import java.util.UUID

/**
 * Event triggered when a file is fetched from a GitHub repository.
 *
 * This event represents the successful retrieval of a file from a repository.
 * It includes essential details such as the file path, content, source URL,
 * and a unique transaction identifier to track the operation.
 * It is used to propagate file-related information within the system during
 * synchronization or processing workflows.
 *
 * @property transactionId A unique identifier for the transaction associated with this fetching event.
 * @property repositoryOwner The owner of the repository where the file was fetched.
 * @property repositoryName The name of the repository where the file was fetched.
 * @property path The file path of the retrieved file within the repository.
 * @property content The content of the fetched file.
 * @property sourceUrl The URL pointing to the location of the fetched file in the repository.
 */
data class GithubFileFetchedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val path: String,
    val content: String,
    val sourceUrl: String,
)
