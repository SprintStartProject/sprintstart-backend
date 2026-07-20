package com.sprintstart.sprintstartbackend.connectors.github.external.events.files

import java.util.UUID

/**
 * Event emitted when the process of fetching multiple files from a GitHub repository is completed.
 *
 * @property transactionId A unique identifier for the transaction associated with this fetching operation.
 * @property repositoryOwner The owner of the repository where the files were fetched.
 * @property repositoryName The name of the repository where the files were fetched.
 */
data class GithubFilesFetchCompletedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
)
