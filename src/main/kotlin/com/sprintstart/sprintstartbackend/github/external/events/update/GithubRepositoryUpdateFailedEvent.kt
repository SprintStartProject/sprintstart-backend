package com.sprintstart.sprintstartbackend.github.external.events.update

import java.util.UUID

data class GithubRepositoryUpdateFailedEvent(
    val transactionId: UUID,
    val reason: String = "Internal server error",
)
