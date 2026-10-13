package com.sprintstart.sprintstartbackend.connectors.notion.model.entity

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpecJpaConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Persists one project-scoped connection to the page scope visible through a Notion credential.
 *
 * Project and credential ownership are stored as scalar identifiers to keep module boundaries
 * explicit. Scheduling belongs to this connection, while individual page versions are tracked in
 * [NotionSyncedPage].
 */
@Entity
@Table(
    name = "notion_workspace_connections",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_notion_workspace_project_credential",
            columnNames = ["project_id", "credential_auth_id", "credential_name"],
        ),
        UniqueConstraint(
            name = "uq_notion_workspace_project_token_owner",
            columnNames = ["project_id", "workspace_id", "token_owner_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_notion_workspace_connection_project", columnList = "project_id"),
        Index(
            name = "idx_notion_workspace_connection_credential",
            columnList = "credential_auth_id,credential_name",
        ),
    ],
)
internal class NotionWorkspaceConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false, updatable = false)
    var projectId: UUID,
    @Column(name = "workspace_id")
    var workspaceId: String? = null,
    @Column(name = "workspace_name", nullable = false, length = 2000)
    var workspaceName: String = "Notion workspace",
    @Column(name = "token_owner_id")
    var tokenOwnerId: String? = null,
    @Column(name = "credential_auth_id", nullable = false)
    var credentialAuthId: String,
    @Column(name = "credential_name", nullable = false)
    var credentialName: String,
    @Column(name = "source_enabled", nullable = false)
    var sourceEnabled: Boolean = true,
    @Column(name = "auto_update", nullable = false)
    var autoUpdate: Boolean = false,
    @Column(name = "schedule", nullable = false)
    var schedule: String = DEFAULT_NOTION_SCHEDULE,
    @Column(name = "schedule_spec", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = ScheduleSpecJpaConverter::class)
    var scheduleSpec: ScheduleSpec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
    @Column(name = "next_sync_at")
    var nextSyncAt: Instant? = null,
    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) {
    @PrePersist
    fun recordCreationTime() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun recordUpdateTime() {
        updatedAt = Instant.now()
    }

    override fun toString(): String {
        return "NotionWorkspaceConnection(id=$id, projectId=$projectId, workspaceId=$workspaceId)"
    }

    val workspaceUrl: String get() = "https://www.notion.so"
}

internal const val DEFAULT_NOTION_SCHEDULE = "0 0 2 * * *"
