package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

/**
 * Response for the update all repositories endpoint.
 */
data class UpdateAllRepositoriesResponse(
    val transactionId: UUID,
)
