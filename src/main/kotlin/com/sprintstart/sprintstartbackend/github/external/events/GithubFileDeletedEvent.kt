package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

data class GithubFileDeletedEvent(
    val transactionId: UUID,
    val path: String,
)
