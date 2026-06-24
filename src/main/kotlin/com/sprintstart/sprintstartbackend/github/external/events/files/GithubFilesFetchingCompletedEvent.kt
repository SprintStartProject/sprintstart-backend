package com.sprintstart.sprintstartbackend.github.external.events.files

import java.util.UUID

data class GithubFilesFetchingCompletedEvent(
    val transactionId: UUID,
)
