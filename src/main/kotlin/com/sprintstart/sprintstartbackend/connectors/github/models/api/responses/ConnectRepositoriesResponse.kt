package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import java.util.UUID

data class ConnectRepositoriesResponse(
    val transactionIdsByRepositoryId: Map<String, UUID>,
)
