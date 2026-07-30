package com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial

import java.util.UUID

data class JiraInstanceConnectionInitiatedEvent(
    val transactionId: UUID,
    val displayName: String,
)
