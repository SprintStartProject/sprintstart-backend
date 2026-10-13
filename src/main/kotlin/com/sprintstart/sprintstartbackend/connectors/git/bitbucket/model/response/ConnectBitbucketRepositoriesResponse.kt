package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

import java.util.UUID

data class ConnectBitbucketRepositoriesResponse(
    val transactionIdsByRepository: Map<String, UUID>,
)
