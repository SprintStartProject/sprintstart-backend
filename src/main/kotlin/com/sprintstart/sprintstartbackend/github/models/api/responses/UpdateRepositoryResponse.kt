package com.sprintstart.sprintstartbackend.github.models.api.responses

import java.util.UUID

/**
 * Response for the update repository endpoint.
 */
data class UpdateRepositoryResponse(
    val transactionId: UUID,
)
