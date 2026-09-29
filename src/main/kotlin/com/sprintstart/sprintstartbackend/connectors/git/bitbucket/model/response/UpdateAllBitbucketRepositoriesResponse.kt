package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

import java.util.UUID

data class UpdateAllBitbucketRepositoriesResponse(
    val transactionIdsByRepository: Map<String, UUID>,
)
