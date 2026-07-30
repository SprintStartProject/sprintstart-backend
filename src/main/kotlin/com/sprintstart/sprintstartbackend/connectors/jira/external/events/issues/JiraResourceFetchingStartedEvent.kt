package com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues

import java.util.UUID

data class JiraResourceFetchingStartedEvent(
    val transactionId: UUID,
)
