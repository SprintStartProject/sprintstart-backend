package com.sprintstart.sprintstartbackend.github.external.events.initial

import java.util.UUID

data class GithubRepositoryConnectionInitiationFailedEvent(
    val transactionId: UUID,
    val reason: String?,
)
