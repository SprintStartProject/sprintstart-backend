package com.sprintstart.sprintstartbackend.connectors.github.external.events.org

import java.util.UUID

/**
 * Signals that a repository under an already-connected GitHub organization was connected.
 *
 * Allows the ingestion module to associate the existing organization metadata artifact
 * with the newly connected project and queue it for AI indexing without re-fetching
 * organization details from GitHub.
 */
data class GithubOrgMetadataAlreadyConnectedEvent(
    val transactionId: UUID,
    val org: String,
)
