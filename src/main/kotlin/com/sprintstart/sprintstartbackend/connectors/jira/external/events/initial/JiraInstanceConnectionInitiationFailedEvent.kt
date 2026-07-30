package com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial

import java.util.UUID

data class JiraInstanceConnectionInitiationFailedEvent(
    val transactionId: UUID,
    val reason: String,
)
