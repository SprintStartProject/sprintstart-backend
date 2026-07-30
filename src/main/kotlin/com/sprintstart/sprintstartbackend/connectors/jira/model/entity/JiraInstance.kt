package com.sprintstart.sprintstartbackend.connectors.jira.model.entity

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "jira_instances")
internal class JiraInstance(
    @Id
    @Column(name = "instance_url", unique = true, nullable = false)
    var instanceUrl: String,
    @Column(name = "display_name")
    var displayName: String,
    @Column(name = "last_update", nullable = false)
    var lastUpdate: Instant = Instant.now(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "jira_instance_project_ids",
        joinColumns = [JoinColumn(name = "instance_url")],
    )
    @Column(name = "project_id", nullable = false)
    var projectIds: MutableSet<UUID> = mutableSetOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "jira_instance_jira_project_keys",
        joinColumns = [JoinColumn(name = "instance_url")],
    )
    @Column(name = "jira_project_key", nullable = false)
    var jiraProjectKeys: MutableSet<String> = mutableSetOf(),
    @Column(name = "source_enabled", nullable = false)
    var sourceEnabled: Boolean = false,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ConnectionState = ConnectionState.UP_TO_DATE,
    @Column(name = "update_credential_name", nullable = false)
    var updateCredentialName: String,
    @Column(name = "update_credential_user_email", nullable = false)
    var updateCredentialUserEmail: String,
)
