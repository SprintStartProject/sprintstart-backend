package com.sprintstart.sprintstartbackend.github.models.api.responses

import java.util.UUID

/**
 * Response for the connect repository endpoint.
 */
data class ConnectRepositoryResponse(
    val transactionId: UUID,
)
