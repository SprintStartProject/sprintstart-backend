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

@Entity
@Table(
    name = "notion_page_connections",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_notion_page_connection_project_page",
            columnNames = ["project_id", "page_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_notion_page_connection_project", columnList = "project_id"),
        Index(
            name = "idx_notion_page_connection_credential",
            columnList = "credential_auth_id,credential_name",
        ),
    ],
)
internal class NotionPageConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false, updatable = false)
    var projectId: UUID,
    @Column(name = "page_id", nullable = false, updatable = false)
    var pageId: String,
    @Column(name = "page_title", nullable = false, length = 2000)
    var pageTitle: String,
    @Column(name = "page_url", nullable = false, length = 2048)
    var pageUrl: String,
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
    @Column(name = "last_edited_time")
    var lastEditedTime: Instant? = null,
    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null,
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
        return "NotionPageConnection(id=$id, projectId=$projectId, pageId=$pageId, sourceEnabled=$sourceEnabled)"
    }
}

internal const val DEFAULT_NOTION_SCHEDULE = "0 0 2 * * *"
