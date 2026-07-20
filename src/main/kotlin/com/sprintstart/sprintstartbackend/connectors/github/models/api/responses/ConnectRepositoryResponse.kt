package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

/**
 * Response for the connect repository endpoint.
 */
data class ConnectRepositoryResponse(
    val transactionId: UUID,
)
