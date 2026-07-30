package com.sprintstart.sprintstartbackend.connectors.jira.model.api.response

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import java.time.Instant
import java.util.UUID

internal data class JiraInstanceDto(
    val instanceUrl: String,
    val displayName: String,
    val lastUpdate: Instant,
    val projectIds: MutableSet<UUID>,
    val sourceEnabled: Boolean,
    val status: String,
    val updateCredentialName: String,
    val updateCredentialUserEmail: String,
)

internal fun JiraInstance.toDto() = JiraInstanceDto(
    instanceUrl = this.instanceUrl,
    displayName = this.displayName,
    lastUpdate = this.lastUpdate,
    projectIds = this.projectIds,
    sourceEnabled = this.sourceEnabled,
    status = this.status.name,
    updateCredentialName = this.updateCredentialName,
    updateCredentialUserEmail = this.updateCredentialUserEmail,
)
