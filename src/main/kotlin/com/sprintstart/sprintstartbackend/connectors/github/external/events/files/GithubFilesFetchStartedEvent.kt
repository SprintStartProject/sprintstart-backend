package com.sprintstart.sprintstartbackend.connectors.github.external.events.files

import java.util.UUID

/**
 * Emitted when the process of fetching files from a GitHub repository starts.
 *
 * @property transactionId A unique identifier for the transaction associated with this fetching operation.
 * @property repositoryOwner The owner of the repository from which files are being fetched.
 * @property repositoryName The name of the repository from which files are being fetched.
 */
data class GithubFilesFetchStartedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
