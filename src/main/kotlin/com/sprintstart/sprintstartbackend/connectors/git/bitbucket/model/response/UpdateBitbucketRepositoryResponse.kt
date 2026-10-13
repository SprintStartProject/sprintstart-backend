package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

import java.util.UUID

/**
 * Response for the single-repository update endpoint.
 *
 * @property transactionId The id the connector's progress events for this update report under.
 */
data class UpdateBitbucketRepositoryResponse(
    val transactionId: UUID,
)
