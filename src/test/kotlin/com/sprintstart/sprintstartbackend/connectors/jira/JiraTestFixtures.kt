package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredentialsId
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

internal fun jiraInstance(
    instanceUrl: String = "https://jira.example.com",
    displayName: String = "Test Jira",
    lastUpdate: Instant = Instant.now(),
    projectIds: MutableSet<UUID> = mutableSetOf(UUID.randomUUID()),
    jiraProjectKeys: MutableSet<String> = mutableSetOf("TEST"),
    sourceEnabled: Boolean = true,
    status: ConnectionState = ConnectionState.UP_TO_DATE,
    updateCredentialName: String = "token",
    updateCredentialUserEmail: String = "user@example.com",
): JiraInstance = JiraInstance(
    instanceUrl = instanceUrl,
    displayName = displayName,
    lastUpdate = lastUpdate,
    projectIds = projectIds,
    jiraProjectKeys = jiraProjectKeys,
    sourceEnabled = sourceEnabled,
    status = status,
    updateCredentialName = updateCredentialName,
    updateCredentialUserEmail = updateCredentialUserEmail,
)

internal fun jiraInstanceConfig(
    instance: JiraInstance = jiraInstance(),
    autoUpdate: Boolean = false,
    schedule: String = "0 0 2 * * *",
    spec: ScheduleSpec = ScheduleSpec.Daily(LocalTime.of(2, 0)),
    nextSyncAt: Instant? = null,
): JiraInstanceConfig {
    val config = JiraInstanceConfig(
        id = instance.instanceUrl,
        instance = instance,
        autoUpdate = autoUpdate,
        schedule = schedule,
        spec = spec,
        nextSyncAt = nextSyncAt,
    )
    return config
}

internal fun jiraCredential(
    userEmail: String = "user@example.com",
    name: String = "token",
    authToken: String = "secret",
): JiraCredential = JiraCredential(
    id = JiraCredentialsId(userEmail, name),
    authToken = authToken,
)
