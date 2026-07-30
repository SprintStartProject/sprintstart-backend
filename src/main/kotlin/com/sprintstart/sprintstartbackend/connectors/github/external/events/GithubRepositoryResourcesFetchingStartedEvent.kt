package com.sprintstart.sprintstartbackend.connectors.github.external.events

import java.util.UUID

/**
 * Emitted when the process of fetching resources for a specific GitHub repository is initiated.
 *
 * @param transactionId A unique identifier representing the transaction or operation initiating the fetch.
 * @param repositoryId The persisted repository connection this ingestion run belongs to.
 * @param owner The username or organization owning the GitHub repository.
 * @param name The name of the GitHub repository whose resources are being fetched.
 */
class GithubRepositoryResourcesFetchingStartedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val owner: String,
    val name: String,
)
