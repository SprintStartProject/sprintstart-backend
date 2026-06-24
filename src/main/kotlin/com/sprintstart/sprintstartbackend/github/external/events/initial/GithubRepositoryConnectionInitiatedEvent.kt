package com.sprintstart.sprintstartbackend.github.external.events.initial

import java.util.UUID

data class GithubRepositoryConnectionInitiatedEvent(
    val transactionId: UUID,
)
