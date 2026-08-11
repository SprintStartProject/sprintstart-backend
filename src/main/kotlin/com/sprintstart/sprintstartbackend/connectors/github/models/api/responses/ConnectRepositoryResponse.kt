package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

/**
 * Response for the connect repository endpoint.
 *
 * [wasReused] is the only signal that an existing connection was reused. The response deliberately
 * excludes linked projects and connection ownership details.
 */
data class ConnectRepositoryResponse(
    val transactionId: UUID,
    val wasReused: Boolean,
)
