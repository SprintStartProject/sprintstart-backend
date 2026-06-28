package com.sprintstart.sprintstartbackend.github.external.events.files

import java.util.UUID

/**
 * Emitted when the process of fetching files from a GitHub repository fails.
 *
 * @property transactionId A unique identifier for the transaction associated with this failure event.
 * @property repositoryOwner The owner of the repository where the files fetch attempt was made.
 * @property repositoryName The name of the repository where the files fetch attempt was made.
 * @property reason A description of the reason for the failure. Defaults to "Internal server error".
 */
data class GithubFilesFetchFailedEvent(
    val transactionId: UUID,
    val repositoryOwner: String,
    val repositoryName: String,
    val reason: String = "Internal server error",
)
