package com.sprintstart.sprintstartbackend.github.external.events.update

import java.util.UUID

data class GithubAllRepositoriesUpdateStartedEvent(
    val transactionId: UUID,
)
