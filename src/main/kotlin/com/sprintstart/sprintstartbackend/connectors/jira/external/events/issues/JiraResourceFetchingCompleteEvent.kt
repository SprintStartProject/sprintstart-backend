package com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues

import java.util.UUID

data class JiraResourceFetchingCompleteEvent(
    val transactionId: UUID,
)
