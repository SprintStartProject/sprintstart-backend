package com.sprintstart.sprintstartbackend.connectors.github.models

import java.util.UUID

/**
 * What connecting a repository actually did.
 *
 * @property transactionId The connection transaction.
 * @property wasReused Whether an already-connected repository was linked to the project instead of
 * being fetched again.
 */
data class RepositoryConnectionOutcome(
    val transactionId: UUID,
    val wasReused: Boolean,
)
