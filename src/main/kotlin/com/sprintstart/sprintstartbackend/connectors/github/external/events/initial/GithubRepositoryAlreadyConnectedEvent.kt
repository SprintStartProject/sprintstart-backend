package com.sprintstart.sprintstartbackend.connectors.github.external.events.initial

import java.util.UUID

/**
 * Signals that a project was linked to an already-connected GitHub repository without ingestion.
 *
 * The ingestion module records this request as a completed no-op run so connection history remains
 * consistent without publishing downstream artifact-sync events.
 */
data class GithubRepositoryAlreadyConnectedEvent(
    val transactionId: UUID,
    val owner: String,
    val name: String,
)
