package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

/**
 * Response for the connect repository endpoint.
 *
 * @property transactionId The connection transaction. A reused connection records a run that
 * completed with no work, so the id always resolves -- but there is no progress to follow.
 * @property wasReused Whether an existing connection was linked instead of a new one being fetched.
 * Deliberately a bare flag: a PM only sees their own projects and must not be able to infer from
 * this response which other project the source was already connected to.
 */
data class ConnectRepositoryResponse(
    val transactionId: UUID,
    val wasReused: Boolean = false,
)
