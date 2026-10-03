package com.sprintstart.sprintstartbackend.connectors.git.github.models.api.responses

import java.util.UUID

/**
 * Response for the update repository endpoint.
 */
data class UpdateRepositoryResponse(
    val transactionId: UUID,
)
