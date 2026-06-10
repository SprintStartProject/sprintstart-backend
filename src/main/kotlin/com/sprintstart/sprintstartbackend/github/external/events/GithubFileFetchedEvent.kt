package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

data class GithubFileFetchedEvent(
    val transactionId: UUID,
)
