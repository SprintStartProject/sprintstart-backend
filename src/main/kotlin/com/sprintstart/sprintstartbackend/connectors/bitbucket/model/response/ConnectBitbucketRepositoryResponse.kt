package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.response

import java.util.UUID

data class ConnectBitbucketRepositoryResponse(
    val transactionId: UUID,
)
